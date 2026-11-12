import { chamar, decidirPreenchimento, evidenciaDeEnvio, lerConfig, paginaValida, PADROES_SENSIVEIS, podeEnviar } from "./comum.js";

// Caminhos da API do envio automático (POST: a extensão só tem POST liberado no CORS).
const CAMINHO_ESTADO_AUTOMATICO = "/api/v1/assist/envio-automatico/estado";
const CAMINHO_LIGA_AUTOMATICA = "/api/v1/assist/envio-automatico";

const situacao = document.getElementById("situacao");
const formulario = document.getElementById("formulario");
const campos = { empresa: document.getElementById("empresa"), cargo: document.getElementById("cargo"), local: document.getElementById("local") };
const botaoPreencher = document.getElementById("preencher");
const situacaoPreencher = document.getElementById("situacao-preencher");
const secaoEnviar = document.getElementById("secao-enviar");
const confirmarEnvio = document.getElementById("confirmar-envio");
const botaoEnviar = document.getElementById("enviar");
const situacaoEnviar = document.getElementById("situacao-enviar");
const secaoAutomatico = document.getElementById("secao-automatico");
const situacaoAutomatico = document.getElementById("situacao-automatico");
const avisoAutomatico = document.getElementById("aviso-automatico");
const aceitarAviso = document.getElementById("aceitar-aviso");
const botaoLigar = document.getElementById("ligar-automatico");
const botaoDesligar = document.getElementById("desligar-automatico");
let rascunho = null;
// Ligado na conta (confirmado pelo servidor): o envio desta vaga sai sem a confirmação de cada envio.
let automaticoLigado = false;

// textContent sempre: o que vem da página é dado, nunca HTML.
const mostrar = (texto) => {
  situacao.textContent = texto;
};
const mostrarPreencher = (texto) => {
  situacaoPreencher.textContent = texto;
};
const mostrarEnviar = (texto) => {
  situacaoEnviar.textContent = texto;
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

// Só mostra o preenchimento e o envio quando a vaga já está salva no JobDragonQuest (a URL é a chave).
async function verificarVagaSalva(config, pagina) {
  try {
    const previa = await chamar(config, "/api/v1/assist/prefill", { url: pagina });
    botaoPreencher.hidden = false;
    secaoEnviar.hidden = false;
    mostrarPreencher(`Vaga salva. Perfil que será usado: ${previa.profileName || "sem nome"}.`);
    secaoAutomatico.hidden = false;
    await carregarAutomatico(config);
  } catch {
    botaoPreencher.hidden = true;
    secaoEnviar.hidden = true;
    secaoAutomatico.hidden = true;
    mostrarPreencher("Para preencher a candidatura, cadastre esta vaga primeiro (formulário acima).");
  }
}

// Mostra o estado do envio automático: bloqueado até o aprendizado, ou liberado com aviso, ou ligado.
function mostrarAutomatico(estado) {
  automaticoLigado = estado.ativo === true;
  const liberado = estado.liberado === true;
  avisoAutomatico.hidden = automaticoLigado || !liberado;
  botaoLigar.hidden = automaticoLigado || !liberado;
  botaoLigar.disabled = !aceitarAviso.checked;
  botaoDesligar.hidden = !automaticoLigado;
  // Ligado, a confirmação de cada envio não é pedida; ainda passa por CAPTCHA e campos sensíveis.
  confirmarEnvio.closest("label").hidden = automaticoLigado;
  if (automaticoLigado) {
    situacaoAutomatico.textContent = "Ligado. A candidatura desta vaga sai sem a sua confirmação; o envio fica registrado com data, hora e onde conferir.";
    botaoEnviar.disabled = false;
    return;
  }
  botaoEnviar.disabled = !confirmarEnvio.checked;
  if (liberado) {
    situacaoAutomatico.textContent = `Liberado: você já confirmou ${estado.confirmados} envios com um clique. Leia o aviso para ligar.`;
  } else {
    situacaoAutomatico.textContent = `Ainda não liberado: ${estado.confirmados} de ${estado.limite} envios com um clique confirmados. Use o envio com um clique até lá, para a IA aprender a preencher.`;
  }
}

async function carregarAutomatico(config) {
  try {
    mostrarAutomatico(await chamar(config, CAMINHO_ESTADO_AUTOMATICO, {}));
  } catch (erro) {
    situacaoAutomatico.textContent = "Não consegui ler o envio automático: " + erro.message;
  }
}

aceitarAviso.addEventListener("change", () => {
  botaoLigar.disabled = !aceitarAviso.checked;
});

async function alterarAutomatico(ativo) {
  const config = await lerConfig(chrome.storage.local);
  try {
    const estado = await chamar(config, CAMINHO_LIGA_AUTOMATICA, { ativo, avisoAceito: ativo && aceitarAviso.checked });
    aceitarAviso.checked = false;
    mostrarAutomatico(estado);
  } catch (erro) {
    situacaoAutomatico.textContent = "Não deu para alterar: " + erro.message;
  }
}

botaoLigar.addEventListener("click", () => alterarAutomatico(true));
botaoDesligar.addEventListener("click", () => alterarAutomatico(false));

confirmarEnvio.addEventListener("change", () => {
  botaoEnviar.disabled = !confirmarEnvio.checked && !automaticoLigado;
});

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

// Funções que rodam na página aberta, só depois do clique. Não usam nada de fora delas, porque o Chrome as copia para a página.
// Lê os campos de texto: não envia nada e não escreve nada.
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

// Detecta CAPTCHA: se houver, a extensão para e a pessoa envia.
function detectarCaptcha() {
  return document.querySelector('iframe[src*="recaptcha"], iframe[src*="hcaptcha"], iframe[src*="turnstile"], .g-recaptcha, .h-captcha, .cf-turnstile, [data-sitekey]') !== null;
}

// Marca um único botão de envio da página. Se houver zero ou mais de um candidato, não marca nada.
function marcarBotaoEnviar() {
  const padrao = /enviar|candidatar|candidatura|submit|apply|send/i;
  const candidatos = [...document.querySelectorAll('button[type=submit], input[type=submit], button:not([type])')]
    .filter((botao) => botao.getClientRects().length > 0 && !botao.disabled)
    .filter((botao) => padrao.test((botao.innerText || botao.value || "").trim()));
  if (candidatos.length !== 1) return false;
  candidatos[0].dataset.jdqEnviar = "1";
  return true;
}

function clicarBotaoEnviar() {
  const botao = document.querySelector('[data-jdq-enviar="1"]');
  if (!botao) return false;
  botao.click();
  return true;
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
    mostrarPreencher(`${feitos.result} campo(s) preenchido(s) com o perfil "${previa.profileName || "sem nome"}". Revise tudo antes de enviar.`);
  } catch (erro) {
    mostrarPreencher("Não deu para preencher: " + erro.message);
  }
});

botaoEnviar.addEventListener("click", async () => {
  botaoEnviar.disabled = true;
  try {
    const config = await lerConfig(chrome.storage.local);
    const { aba, pagina } = await paginaAtual();
    if (!pagina) {
      mostrarEnviar("Abra a página da vaga.");
      return;
    }
    // Revalida os campos logo antes do envio; a confirmação precisa estar marcada.
    const [captcha] = await chrome.scripting.executeScript({ target: { tabId: aba.id }, func: detectarCaptcha });
    const [lidos] = await chrome.scripting.executeScript({ target: { tabId: aba.id }, func: lerCamposDaPagina, args: [PADROES_SENSIVEIS.map((padrao) => padrao.source)] });
    const pode = podeEnviar({ campos: lidos.result, captcha: captcha.result, confirmado: confirmarEnvio.checked, automatico: automaticoLigado });
    if (!pode.ok) {
      mostrarEnviar(pode.motivo);
      botaoEnviar.disabled = false;
      return;
    }
    const [achou] = await chrome.scripting.executeScript({ target: { tabId: aba.id }, func: marcarBotaoEnviar });
    if (!achou.result) {
      mostrarEnviar("Não achei um único botão de envio nesta página. Envie você mesmo e registre no painel.");
      botaoEnviar.disabled = false;
      return;
    }
    await chrome.scripting.executeScript({ target: { tabId: aba.id }, func: clicarBotaoEnviar });
    const agora = new Date();
    try {
      await chamar(config, "/api/v1/assist/submitted", {
        url: pagina,
        evidence: evidenciaDeEnvio(pagina, agora, automaticoLigado),
        automatico: automaticoLigado,
      });
      mostrarEnviar(`Candidatura enviada e registrada em ${agora.toLocaleString("pt-BR")}. Confira no site: ${pagina}`);
    } catch (erro) {
      mostrarEnviar(`A página foi enviada, mas não consegui registrar no JobDragonQuest (${erro.message}). Confira no site: ${pagina}`);
    }
  } catch (erro) {
    mostrarEnviar("Não deu para enviar: " + erro.message);
    botaoEnviar.disabled = false;
  }
});

iniciar();
