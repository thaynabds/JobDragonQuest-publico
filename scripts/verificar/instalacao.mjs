// G21: instalação em um comando e conexões (MCP e Congo) sem expor o token.
import { randomBytes } from "node:crypto";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const SDK = join(ROOT, "src", "jobfly-mcp", "node_modules", "@modelcontextprotocol", "sdk", "dist", "esm");
const API = process.env.JOBFLY_API_URL || "http://127.0.0.1:8080";
const mode = (path) => statSync(path).mode & 0o777;

for (const script of ["instalar.sh", "conectar-congo.sh", "iniciar.sh", "configurar-local.sh"]) {
  check(`${script} sem erro de sintaxe`, run("bash", ["-n", join("scripts", script)]).status === 0);
}
const prereq = run("bash", ["scripts/instalar.sh", "--verificar"]);
check("instalar.sh confere os pré-requisitos", prereq.status === 0 && prereq.stdout.includes("PRE_REQUISITOS_OK"), prereq.stdout + prereq.stderr);
check("instalar.sh recusa opção desconhecida", run("bash", ["scripts/instalar.sh", "--qualquer"]).status === 1);

// Conta e token reais para provar que a configuração gerada funciona de ponta a ponta.
async function call(method, path, token, body) {
  const response = await fetch(API + path, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : null };
}
const session = (await call("POST", "/api/v1/auth/register", null, {
  email: `instalacao-${randomBytes(5).toString("hex")}@teste.dev`,
  password: randomBytes(18).toString("base64url"),
  displayName: "Teste Instalação",
})).body?.accessToken;
const token = (await call("POST", "/api/v1/tokens", session, { name: "Instalação", days: 1 })).body?.value ?? "";
check("token real criado para o teste", /^jfp_/.test(token));

const dir = mkdtempSync(join(tmpdir(), "jobfly-instalacao-"));
try {
  const config = join(dir, "cliente", "mcp.json");
  const tokenFile = join(dir, "segredos", "mcp-token");
  mkdirSync(join(dir, "cliente"));
  writeFileSync(config, JSON.stringify({ mcpServers: { outro: { command: "outro" } } }));
  const env = { ...process.env, JOBFLY_TOKEN: token };
  const connected = run(process.execPath, ["scripts/conectar-mcp.mjs", "--cliente", "cursor", "--config", config, "--token-arquivo", tokenFile], { env });
  check("conectar-mcp grava a configuração do cliente", connected.status === 0 && connected.stdout.includes("MCP_CONECTADO_OK"), connected.stderr);
  const saved = existsSync(config) ? JSON.parse(readFileSync(config, "utf8")) : {};
  check("mantém os outros servidores do cliente", saved.mcpServers?.outro?.command === "outro");
  check("configuração aponta para o arquivo do token", saved.mcpServers?.jobfly?.env?.JOBFLY_TOKEN_FILE === tokenFile);
  check("o token não aparece na configuração do cliente nem na saída", !readFileSync(config, "utf8").includes(token) && !connected.stdout.includes(token));
  check("arquivo do token e configuração com permissão 600", existsSync(tokenFile) && mode(tokenFile) === 0o600 && mode(config) === 0o600);
  check("cópia de segurança da configuração anterior", existsSync(`${config}.bak`));

  const broken = join(dir, "quebrado.json");
  writeFileSync(broken, "{ isto não é json");
  const refused = run(process.execPath, ["scripts/conectar-mcp.mjs", "--cliente", "cursor", "--config", broken, "--token-arquivo", tokenFile], { env });
  check("JSON inválido do cliente não é sobrescrito", refused.status === 1 && readFileSync(broken, "utf8") === "{ isto não é json");
  const remote = run(process.execPath, ["scripts/conectar-mcp.mjs", "--cliente", "json", "--api", "http://evil.example.com", "--token-arquivo", tokenFile], { env });
  check("recusa API remota sem HTTPS", remote.status === 1);
  const badToken = run(process.execPath, ["scripts/conectar-mcp.mjs", "--cliente", "json", "--token-arquivo", tokenFile], { env: { ...process.env, JOBFLY_TOKEN: "jfp_curto" } });
  check("recusa token malformado", badToken.status === 1);
  const json = run(process.execPath, ["scripts/conectar-mcp.mjs", "--cliente", "json", "--token-arquivo", tokenFile], { env });
  check("modo json mostra o bloco sem o token", json.status === 0 && json.stdout.includes("JOBFLY_TOKEN_FILE") && !json.stdout.includes(token));

  // Sobe o servidor MCP exatamente como o cliente de IA subiria, pela entrada gravada.
  const entry = saved.mcpServers?.jobfly;
  const { Client } = await import(join(SDK, "client", "index.js"));
  const { StdioClientTransport } = await import(join(SDK, "client", "stdio.js"));
  const client = new Client({ name: "verificacao-instalacao", version: "1.0.0" });
  try {
    await client.connect(new StdioClientTransport({ command: entry.command, args: entry.args, env: { PATH: process.env.PATH ?? "", ...entry.env }, stderr: "pipe" }));
    const result = await client.callTool({ name: "jobfly_resumo", arguments: {} });
    check("o cliente de IA usa o JobFly real com a configuração gerada", !result.isError && JSON.stringify(result.content).includes("jobsFound"));
  } catch (error) {
    check("o cliente de IA usa o JobFly real com a configuração gerada", false, String(error));
  } finally {
    await client.close().catch(() => undefined);
  }

  // Congo: pasta falsa com a estrutura do bot.
  const congo = join(dir, "congo");
  mkdirSync(join(congo, "plugins"), { recursive: true });
  mkdirSync(join(congo, "src", "congo_discord"), { recursive: true });
  const congoConfig = join(dir, "jobfly-config", "congo.env");
  const congoEnv = { ...process.env, JOBFLY_TOKEN: token, JOBFLY_DISCORD_USER_ID: "123456789012345678", JOBFLY_CONGO_CONFIG: congoConfig };
  const linked = run("bash", ["scripts/conectar-congo.sh", "--congo", congo, "--sem-reiniciar"], { env: congoEnv, input: "" });
  check("conectar-congo instala o plugin e grava a configuração", linked.status === 0 && linked.stdout.includes("CONGO_CONECTADO_OK"), linked.stderr);
  check("plugin copiado para a pasta plugins do Congo", existsSync(join(congo, "plugins", "jobfly.py")));
  check("configuração do Congo com permissão 600", existsSync(congoConfig) && mode(congoConfig) === 0o600);
  check("o token não aparece na saída do conectar-congo", !linked.stdout.includes(token));
  const noCongo = run("bash", ["scripts/conectar-congo.sh", "--congo", join(dir, "nao-existe"), "--sem-reiniciar"], { env: congoEnv, input: "" });
  check("conectar-congo recusa pasta que não é do Congo", noCongo.status === 1);
} finally {
  rmSync(dir, { recursive: true, force: true });
  await call("GET", "/api/v1/tokens", session).then(async (list) => {
    for (const item of list.body ?? []) await call("DELETE", `/api/v1/tokens/${item.id}`, session);
  });
}
finish("INSTALACAO_OK");
