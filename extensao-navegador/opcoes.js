import { apiValida, tokenValido } from "./comum.js";

const situacao = document.getElementById("situacao");
document.getElementById("opcoes").addEventListener("submit", async (evento) => {
  evento.preventDefault();
  const api = apiValida(document.getElementById("api").value);
  const token = tokenValido(document.getElementById("token").value);
  if (!api) {
    situacao.textContent = "Use http://127.0.0.1:PORTA, http://localhost:PORTA ou https://seu-servidor, sem caminho.";
    return;
  }
  if (!token) {
    situacao.textContent = "Token inválido: copie o token pessoal inteiro (começa com jdq_).";
    return;
  }
  // Servidor em HTTPS: o navegador pede a permissão só para esse endereço.
  if (api.startsWith("https://") && !(await chrome.permissions.request({ origins: [`${api}/*`] }))) {
    situacao.textContent = "Sem a permissão para esse endereço, a extensão não consegue enviar as vagas.";
    return;
  }
  await chrome.storage.local.set({ api, token });
  document.getElementById("token").value = "";
  situacao.textContent = "Salvo.";
});
