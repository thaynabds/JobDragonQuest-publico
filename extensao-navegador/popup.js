import { chamar, lerConfig, paginaValida } from "./comum.js";

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

iniciar();
