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

/** Documento, deficiência, raça, gênero, orientação e consentimentos: a extensão nunca preenche, mesmo com resposta no perfil. */
export const PADROES_SENSIVEIS = [
  /cpf|cnpj|\brg\b|documento|identidade|passport|\bssn\b|tax id|national id/,
  /defici|pcd|disabilit|handicap/,
  /raca|etnia|racial|ethnic|\brace\b/,
  /orientacao sexual|sexual orientation|sexuality/,
  /genero|gender|pronome|pronoun/,
  /consentimento|consent|demograph|demografic|diversidade|diversity|lgpd|privacidade|privacy/,
];

const normalizarRotulo = (texto) =>
  String(texto ?? "").normalize("NFD").replace(/\p{M}/gu, "").toLowerCase().replace(/[*:?]/g, "").replace(/\s+/g, " ").trim();

/** O rótulo, o nome ou o contexto do campo menciona algo sensível? */
export function ehSensivel(texto) {
  const normal = normalizarRotulo(texto);
  return PADROES_SENSIVEIS.some((padrao) => padrao.test(normal));
}

const TIPOS_DE_TEXTO = new Set(["text", "email", "tel", "url", "textarea"]);
const NOME = ["nome", "first name", "given name", "primeiro nome"];
const SOBRENOME = ["sobrenome", "last name", "family name", "surname"];
const EMAIL = ["email", "e-mail", "e-mail address", "email address", "endereco de e-mail"];
const TELEFONE = ["telefone", "phone", "phone number", "celular", "mobile", "mobile phone", "whatsapp"];
const LINK_DO_PERFIL = { linkedin: /linkedin/, github: /github/, portfolio: /portfolio|portfólio|website/ };

/**
 * Decide o que colocar em cada campo de texto vazio, com os dados da vaga salva no JobDragonQuest (previa). Nunca mexe em
 * campo sensível, em campo já preenchido, em select ou arquivo, nem usa resposta que falte no perfil.
 * Devolve [{indice, valor}]; quem aplica é a própria página, e nada é enviado.
 */
export function decidirPreenchimento(campos, previa) {
  const partes = String(previa?.name ?? "").trim().split(/\s+/).filter(Boolean);
  const contato = { nome: partes[0] ?? null, sobrenome: partes.slice(1).join(" ") || null, email: previa?.email ?? null, telefone: previa?.phone ?? null };
  const respostas = (previa?.answers ?? []).filter((resposta) => resposta.answer != null && resposta.answer !== "" && resposta.missing == null);
  const valores = [];
  for (const campo of campos ?? []) {
    if (campo.vazio !== true || !TIPOS_DE_TEXTO.has(campo.tipo) || campo.sensivel || ehSensivel(campo.contexto ?? campo.rotulo)) continue;
    const valor = valorParaRotulo(normalizarRotulo(campo.rotulo), contato, respostas);
    if (valor) valores.push({ indice: campo.indice, valor: String(valor) });
  }
  return valores;
}

function valorParaRotulo(rotulo, contato, respostas) {
  if (!rotulo) return null;
  if (NOME.includes(rotulo)) return contato.nome;
  if (SOBRENOME.includes(rotulo)) return contato.sobrenome;
  if (EMAIL.includes(rotulo)) return contato.email;
  if (TELEFONE.includes(rotulo)) return contato.telefone;
  const link = Object.keys(LINK_DO_PERFIL).find((chave) => LINK_DO_PERFIL[chave].test(rotulo));
  if (link) {
    const achado = respostas.find((resposta) => resposta.category === link);
    return achado ? achado.answer : null;
  }
  const pergunta = respostas.find((resposta) => normalizarRotulo(resposta.question) === rotulo);
  return pergunta ? pergunta.answer : null;
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

/**
 * Pode enviar? Só com a confirmação marcada, sem CAPTCHA na página e com todo campo sensível preenchido pela pessoa
 * (a extensão nunca preenche esses campos, então se algum estiver vazio o envio para aqui).
 */
export function podeEnviar({ campos, captcha, confirmado, automatico = false }) {
  // Automático dispensa a confirmação de cada vaga, mas CAPTCHA e campo sensível vazio continuam bloqueando.
  if (automatico !== true && confirmado !== true) return { ok: false, motivo: "Marque a confirmação antes de enviar." };
  if (captcha === true) return { ok: false, motivo: "Esta página tem CAPTCHA: envie você mesmo." };
  const sensiveisVazios = (campos ?? []).filter((campo) => campo.sensivel === true && campo.vazio === true);
  if (sensiveisVazios.length > 0) {
    return { ok: false, motivo: "Há campos sensíveis vazios (documento, deficiência, raça, gênero ou consentimentos). Complete você mesmo." };
  }
  return { ok: true, motivo: null };
}

/** Evidência do envio para a candidatura: data, hora, como saiu e onde conferir. */
export function evidenciaDeEnvio(url, agora = new Date(), automatico = false) {
  const modo = automatico ? "pelo envio automático que a pessoa deixou ligado" : "com confirmação da pessoa";
  return `Enviada pela extensão em ${agora.toISOString()}, ${modo}. Conferir em: ${url}`;
}
