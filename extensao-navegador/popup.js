import { campoDoPerfil, chamar, lerConfig, paginaValida } from "./comum.js";

const situacao = document.getElementById("situacao");
const formulario = document.getElementById("formulario");
const campos = { empresa: document.getElementById("empresa"), cargo: document.getElementById("cargo"), local: document.getElementById("local") };
let rascunho = null;

// textContent sempre: o que vem da página é dado, nunca HTML.
const mostrar = (texto) => {
  situacao.textContent = texto;
};

async function iniciar() {
  const config = await lerConfig(chrome.storage.local);
  if (!config.api || !config.token) {
    mostrar("Configure a API local e o token pessoal (link abaixo).");
    return;
  }
  const [aba] = await chrome.tabs.query({ active: true, currentWindow: true });
  const pagina = paginaValida(aba && aba.url);
  if (!pagina) {
    mostrar("Abra a página de uma vaga (http ou https) e tente de novo.");
    return;
  }
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

formulario.addEventListener("submit", async (evento) => {
  evento.preventDefault();
  const config = await lerConfig(chrome.storage.local);
  const vaga = { ...rascunho, company: campos.empresa.value.trim(), title: campos.cargo.value.trim(), location: campos.local.value.trim() || null };
  try {
    await chamar(config, "/api/v1/jobs", vaga);
    formulario.hidden = true;
    mostrar("Vaga cadastrada no JobDragonQuest.");
  } catch (erro) {
    mostrar("Não deu para cadastrar: " + erro.message);
  }
});

// Rodam na página aberta, só depois do clique. Nunca enviam o formulário e não tocam em campo já preenchido.
function lerRotulos() {
  return [...document.querySelectorAll("input:not([type=hidden]):not([type=password]):not([type=file]), textarea")].map((campo) =>
    campo.value || campo.disabled || campo.readOnly ? "" : [campo.labels?.[0]?.innerText, campo.name, campo.id, campo.placeholder, campo.getAttribute("aria-label")].join(" "));
}

function aplicarValores(valores) {
  const campos = [...document.querySelectorAll("input:not([type=hidden]):not([type=password]):not([type=file]), textarea")];
  let total = 0;
  valores.forEach((valor, indice) => {
    const campo = campos[indice];
    if (!valor || !campo || campo.value) return;
    campo.value = valor;
    campo.dispatchEvent(new Event("input", { bubbles: true }));
    campo.dispatchEvent(new Event("change", { bubbles: true }));
    total += 1;
  });
  return total;
}

document.getElementById("preencher").addEventListener("click", async () => {
  try {
    const config = await lerConfig(chrome.storage.local);
    const perfis = await chamar(config, "/api/v1/profiles");
    const p = await chamar(config, "/api/v1/profiles/" + encodeURIComponent(perfis[0].id));
    const o = p.options ?? {};
    const dados = { nome: o.socialName ?? p.name, email: o.contactEmail, telefone: o.phone, cidade: p.city, linkedin: p.linkedinUrl, github: p.githubUrl, portfolio: p.portfolioUrl };
    const [aba] = await chrome.tabs.query({ active: true, currentWindow: true });
    const [rotulos] = await chrome.scripting.executeScript({ target: { tabId: aba.id }, func: lerRotulos });
    const valores = rotulos.result.map((rotulo) => dados[campoDoPerfil(rotulo)] ?? null);
    const [resultado] = await chrome.scripting.executeScript({ target: { tabId: aba.id }, func: aplicarValores, args: [valores] });
    mostrar(resultado.result + " campo(s) preenchido(s). Confira e envie você mesma.");
  } catch (erro) {
    mostrar("Não deu para preencher: " + erro.message);
  }
});

iniciar();
