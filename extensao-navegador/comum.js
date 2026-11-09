// Regras compartilhadas pelo popup e pelas opções. Sem dependências e sem servidor de terceiros.
// O token fica só no armazenamento local da extensão (nunca sincronizado com a conta do navegador).

/** API local (http://127.0.0.1 ou http://localhost) ou servidor em HTTPS, sempre sem caminho. Fora disso, o token iria sem criptografia. */
export function apiValida(texto) {
  try {
    const url = new URL(String(texto).trim());
    const local = url.hostname === "127.0.0.1" || url.hostname === "localhost";
    const semCaminho = (url.pathname === "/" || url.pathname === "") && !url.search && !url.hash && !url.username && !url.password;
    return semCaminho && (url.protocol === "https:" || (url.protocol === "http:" && local)) ? url.origin : null;
  } catch {
    return null;
  }
}

/** Token pessoal do JobDragonQuest: prefixo jdq_ e só caracteres seguros. */
export function tokenValido(texto) {
  const token = String(texto ?? "").trim();
  return /^jdq_[A-Za-z0-9_-]{20,200}$/.test(token) ? token : null;
}

/** Só páginas http(s) podem ser enviadas (nada de chrome://, file:// ou páginas internas). */
export function paginaValida(texto) {
  try {
    const url = new URL(String(texto));
    return url.protocol === "https:" || url.protocol === "http:" ? url.href : null;
  } catch {
    return null;
  }
}

export async function lerConfig(armazenamento) {
  const dados = await armazenamento.get(["api", "token"]);
  return { api: apiValida(dados.api ?? "http://127.0.0.1:8080"), token: tokenValido(dados.token) };
}

/** Dado do perfil que corresponde ao rótulo de um campo. Senha, documento e pagamento nunca são preenchidos. */
export function campoDoPerfil(rotulo) {
  const texto = String(rotulo ?? "").normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
  if (/senha|password|cpf|ssn|cartao|card|cvv|banc|bank/.test(texto)) return null;
  const regras = [["linkedin", /linkedin/], ["github", /github/], ["portfolio", /portfolio|website|site pessoal/], ["email", /e-?mail|correo/], ["telefone", /telefone|celular|phone|mobile|whatsapp|telefono/], ["cidade", /cidade|city|ciudad/], ["nome", /nome|name|nombre/]];
  const achado = regras.find(([, padrao]) => padrao.test(texto));
  return achado ? achado[0] : null;
}

/** Chamada à API local com o token no cabeçalho; nunca envia cookies. */
export async function chamar(config, caminho, corpo) {
  const resposta = await fetch(config.api + caminho, {
    method: corpo === undefined ? "GET" : "POST",
    credentials: "omit",
    redirect: "error",
    headers: { ...(corpo === undefined ? {} : { "Content-Type": "application/json" }), Authorization: "Bearer " + config.token },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
  });
  const texto = await resposta.text();
  let dados = null;
  try {
    dados = texto ? JSON.parse(texto) : null;
  } catch {
    dados = null;
  }
  if (!resposta.ok) {
    const erro = new Error((dados && dados.detail) || "Erro " + resposta.status);
    erro.code = dados && dados.code;
    throw erro;
  }
  return dados;
}
