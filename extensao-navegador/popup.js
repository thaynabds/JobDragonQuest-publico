import { chamar, decidirPreenchimento, lerConfig, paginaValida, PADROES_SENSIVEIS } from "./comum.js";

const situacao = document.getElementById("situacao");
const formulario = document.getElementById("formulario");
const campos = { empresa: document.getElementById("empresa"), cargo: document.getElementById("cargo"), local: document.getElementById("local") };
const botaoPreencher = document.getElementById("preencher");
const situacaoPreencher = document.getElementById("situacao-preencher");
let rascunho = null;

// textContent sempre: o que vem da página é dado, nunca HTML.
const mostrar = (texto) => {
  situacao.textContent = texto;
};
const mostrarPreencher = (texto) => {
  situacaoPreencher.textContent = texto;
};

async function paginaAtual() {
  const [aba] = await chrome.tabs.query({ active: true, currentWindow: true });
  return { aba, pagina: paginaValida(aba && aba.url) };
}

async function iniciar() {
  const config = await lerConfig(chrome.storage.local);
  if (!config.api || !config.token) {
    mostrar("Configure a API local e o token pessoal (link abaixo).");
    return;
  }
  const { pagina } = await paginaAtual();
  if (!pagina) {
    mostrar("Abra a página de uma vaga (http ou https) e tente de novo.");
    return;
  }
  verificarVagaSalva(config, pagina);
  try {
    const resposta = await chamar(config, "/api/v1/jobs/from-link", { url: pagina });
    rascunho = resposta.job;
    campos.empresa.value = rascunho.company || "";
    campos.cargo.value = rascunho.title || "";
    campos.local.value = rascunho.location || "";
    formulario.hidden = false;
    mostrar(resposta.extractedFrom === "JSON_LD" ? "Dados da vaga lidos. Confira e cadastre." : "Li só o básico da página. Confira e complete.");
  } catch (erro) {
    mostrar("Não deu para ler a vaga: " + erro.message);
  }
}

// Só mostra o botão de preencher quando a vaga já está salva no JobDragonQuest (a URL é a chave).
async function verificarVagaSalva(config, pagina) {
  try {
    const previa = await chamar(config, "/api/v1/assist/prefill", { url: pagina });
    botaoPreencher.hidden = false;
    mostrarPreencher(`Vaga salva. Perfil que será usado: ${previa.profileName || "sem nome"}.`);
  } catch {
    botaoPreencher.hidden = true;
    mostrarPreencher("Para preencher a candidatura, cadastre esta vaga primeiro (formulário acima).");
  }
}

formulario.addEventListener("submit", async (evento) => {
  evento.preventDefault();
  const config = await lerConfig(chrome.storage.local);
  const vaga = { ...rascunho, company: campos.empresa.value.trim(), title: campos.cargo.value.trim(), location: campos.local.value.trim() || null };
  try {
    await chamar(config, "/api/v1/jobs", vaga);
    formulario.hidden = true;
    mostrar("Vaga cadastrada no JobDragonQuest.");
    const { pagina } = await paginaAtual();
    if (pagina) verificarVagaSalva(config, pagina);
  } catch (erro) {
    mostrar("Não deu para cadastrar: " + erro.message);
  }
});

// Funções que rodam na página aberta, só depois do clique. Não enviam nada e só escrevem em campo de texto vazio.
// Não usam nada de fora delas, porque o Chrome as copia para a página.
function lerCamposDaPagina(padroes) {
  const sensiveis = padroes.map((fonte) => new RegExp(fonte));
  const normal = (texto) => String(texto ?? "").normalize("NFD").replace(/\p{M}/gu, "").toLowerCase().replace(/[*:?]/g, "").replace(/\s+/g, " ").trim();
  const visivel = (campo) => campo.getClientRects().length > 0 && !campo.disabled && !campo.readOnly;
  const rotuloDe = (campo) => {
    const rotulo = campo.id ? document.querySelector(`label[for="${CSS.escape(campo.id)}"]`) : null;
    return [rotulo?.innerText, campo.getAttribute("aria-label"), campo.placeholder].map((texto) => (texto ?? "").trim()).find(Boolean) ?? "";
  };
  const contextoDe = (campo) => [rotuloDe(campo), campo.name, campo.id, campo.closest("label")?.innerText].filter(Boolean).join(" ");
  const seletor = "input:not([type=hidden]):not([type=password]):not([type=file]):not([type=checkbox]):not([type=radio]):not([type=submit]):not([type=button]):not([type=reset]), textarea";
  return [...document.querySelectorAll(seletor)].filter(visivel).map((campo, indice) => {
    campo.dataset.jdqIndice = String(indice);
    const contexto = contextoDe(campo);
    return {
      indice,
      rotulo: rotuloDe(campo),
      contexto,
      tipo: campo.tagName === "TEXTAREA" ? "textarea" : campo.type,
      vazio: campo.value === "",
      sensivel: sensiveis.some((padrao) => padrao.test(normal(contexto))),
    };
  });
}

function aplicarPreenchimento(atribuicoes) {
  let total = 0;
  for (const { indice, valor } of atribuicoes) {
    const campo = document.querySelector(`[data-jdq-indice="${indice}"]`);
    if (!campo || campo.value !== "") continue;
    const descritor = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(campo), "value");
    if (!descritor || !descritor.set) continue;
    descritor.set.call(campo, valor);
    campo.dispatchEvent(new Event("input", { bubbles: true }));
    campo.dispatchEvent(new Event("change", { bubbles: true }));
    total += 1;
  }
  return total;
}

botaoPreencher.addEventListener("click", async () => {
  try {
    const config = await lerConfig(chrome.storage.local);
    const { aba, pagina } = await paginaAtual();
    if (!pagina) {
      mostrarPreencher("Abra a página da vaga.");
      return;
    }
    const previa = await chamar(config, "/api/v1/assist/prefill", { url: pagina });
    const [lidos] = await chrome.scripting.executeScript({ target: { tabId: aba.id }, func: lerCamposDaPagina, args: [PADROES_SENSIVEIS.map((padrao) => padrao.source)] });
    const atribuicoes = decidirPreenchimento(lidos.result, previa);
    const [feitos] = await chrome.scripting.executeScript({ target: { tabId: aba.id }, func: aplicarPreenchimento, args: [atribuicoes] });
    mostrarPreencher(`${feitos.result} campo(s) preenchido(s) com o perfil "${previa.profileName || "sem nome"}". Revise tudo e envie você mesmo: a extensão não envia nada.`);
  } catch (erro) {
    mostrarPreencher("Não deu para preencher: " + erro.message);
  }
});

iniciar();
