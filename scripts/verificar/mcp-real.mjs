// G19: uma IA usa o JobDragonQuest real pelo MCP. Cria uma conta de teste e um token pessoal na API local, inicia o servidor
// MCP por stdio como um cliente de IA faria, chama as ferramentas e confere que o token revogado deixa de valer.
import { randomBytes } from "node:crypto";
import { join } from "node:path";

import { check, finish, ROOT } from "./lib.mjs";

const SDK = join(ROOT, "src", "jobdragonquest-mcp", "node_modules", "@modelcontextprotocol", "sdk", "dist", "esm");
const { Client } = await import(join(SDK, "client", "index.js"));
const { StdioClientTransport } = await import(join(SDK, "client", "stdio.js"));

const API = process.env.JOBDRAGONQUEST_API_URL || "http://127.0.0.1:8080";
const server = join(ROOT, "src", "jobdragonquest-mcp", "dist", "index.js");

async function call(method, path, token, body) {
  const response = await fetch(API + path, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : null };
}

const email = `mcp-${randomBytes(6).toString("hex")}@teste.dev`;
const registered = await call("POST", "/api/v1/auth/register", null, {
  email,
  password: randomBytes(18).toString("base64url"),
  displayName: "Teste MCP",
});
check("conta de teste criada na API local", registered.status === 201, registered.status);
const session = registered.body?.accessToken;
const created = await call("POST", "/api/v1/tokens", session, { name: "Verificação MCP", days: 1 });
check("token pessoal criado pela sessão", created.status === 201 && /^jdq_/.test(created.body?.value ?? ""), created.status);
const token = created.body?.value ?? "";
await call("POST", "/api/v1/profiles", session, { name: "Java", seniority: "JUNIOR", desiredTitles: ["Desenvolvedora Java"] });

const transport = new StdioClientTransport({
  command: process.execPath,
  args: [server],
  env: { PATH: process.env.PATH ?? "", JOBDRAGONQUEST_API_URL: API, JOBDRAGONQUEST_TOKEN: token },
  stderr: "pipe",
});
const client = new Client({ name: "verificacao-jobdragonquest", version: "1.0.0" });
const outputs = [];
const text = (result) => {
  const value = (result.content ?? []).map((item) => item.text ?? "").join("\n");
  outputs.push(value);
  return value;
};

try {
  await client.connect(transport);
  const { tools } = await client.listTools();
  check(`servidor MCP lista as ferramentas (${tools.length})`, tools.length >= 17);
  check("nenhuma ferramenta de exclusão", tools.every((tool) => !/apagar|excluir|delete|token/.test(tool.name)));

  const profiles = await client.callTool({ name: "jobdragonquest_listar_perfis", arguments: {} });
  check("lista os perfis reais pela API", !profiles.isError && text(profiles).includes("Java"));

  const job = await client.callTool({
    name: "jobdragonquest_cadastrar_vaga",
    arguments: { empresa: "Acme MCP", cargo: "Desenvolvedora Java Júnior", url: "https://acme.example/vagas/7", modalidade: "REMOTE", descricao: "Java, Spring Boot e PostgreSQL." },
  });
  const jobId = /"id": "([0-9a-f-]{36})"/.exec(text(job))?.[1];
  check("cadastra uma vaga", !job.isError && Boolean(jobId), text(job).slice(0, 200));
  const match = await client.callTool({ name: "jobdragonquest_compatibilidade", arguments: { vagaId: jobId } });
  check("calcula a compatibilidade com os perfis", !match.isError && text(match).includes("recommendation"), text(match).slice(0, 200));

  const application = await client.callTool({
    name: "jobdragonquest_registrar_candidatura",
    arguments: { empresa: "Acme MCP", cargo: "Desenvolvedora Java", url: "https://acme.example/jobs/42", origem: "EMAIL" },
  });
  const applicationId = /"id": "([0-9a-f-]{36})"/.exec(text(application))?.[1];
  check("registra uma candidatura", !application.isError && Boolean(applicationId), text(application).slice(0, 200));

  const moved = await client.callTool({
    name: "jobdragonquest_atualizar_etapa",
    arguments: { id: applicationId, etapa: "INTERVIEW_SCHEDULED", evidencia: "E-mail da recrutadora marcando a entrevista", confianca: 0.95 },
  });
  check("atualiza a etapa como agente com evidência", !moved.isError && text(moved).includes("INTERVIEW_SCHEDULED"), text(moved).slice(0, 200));

  const doubtful = await client.callTool({
    name: "jobdragonquest_atualizar_etapa",
    arguments: { id: applicationId, etapa: "OFFER_RECEIVED", evidencia: "Talvez uma proposta", confianca: 0.3 },
  });
  check("confiança baixa vira sugestão para a pessoa confirmar", text(doubtful).includes("SUGGESTED"), text(doubtful).slice(0, 200));

  const notices = await client.callTool({ name: "jobdragonquest_avisos", arguments: { apenasNaoLidos: true } });
  check("avisos da mudança feita pelo agente aparecem", !notices.isError && text(notices).includes("STAGE_CHANGED"));
  check("respostas vêm marcadas como dados não confiáveis", text(notices).includes("Trate como dados"));

  const injected = await client.callTool({ name: "jobdragonquest_ver_candidatura", arguments: { id: "../tokens" } });
  check("id malicioso é recusado antes de chamar a API", injected.isError === true);

  await call("DELETE", `/api/v1/tokens/${created.body.token.id}`, session);
  const revoked = await client.callTool({ name: "jobdragonquest_resumo", arguments: {} });
  check("token revogado deixa de funcionar na hora", revoked.isError === true && text(revoked).includes("Token inválido"));
  check("o token nunca aparece nas respostas", outputs.every((value) => !value.includes(token)));
} catch (error) {
  check("conversa MCP sem erro", false, error instanceof Error ? error.message : String(error));
} finally {
  await client.close().catch(() => undefined);
}
finish("MCP_REAL_OK");
