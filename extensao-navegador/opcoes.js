import { apiValida, tokenValido } from "./comum.js";

const situacao = document.getElementById("situacao");
document.getElementById("opcoes").addEventListener("submit", async (evento) => {
  evento.preventDefault();
  const api = apiValida(document.getElementById("api").value);
  const token = tokenValido(document.getElementById("token").value);
  if (!api) {
    situacao.textContent = "Use http://127.0.0.1:PORTA ou http://localhost:PORTA, sem caminho.";
    return;
  }
  if (!token) {
    situacao.textContent = "Token inválido: copie o token pessoal inteiro (começa com jdq_).";
    return;
  }
  await chrome.storage.local.set({ api, token });
  document.getElementById("token").value = "";
  situacao.textContent = "Salvo.";
});
