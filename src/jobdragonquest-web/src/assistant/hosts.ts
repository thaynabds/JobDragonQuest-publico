// Hosts que o chat com a IA pode chamar direto do navegador (entram no connect-src da CSP): a chave da pessoa vai só
// para o provedor que ela escolheu, e o Ollama só no próprio computador. Nenhum outro destino externo.
export const AI_HOSTS = "https://api.anthropic.com https://api.openai.com http://127.0.0.1:* http://localhost:*";
