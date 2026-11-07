#!/usr/bin/env node
// Liga um cliente de IA ao JobDragonQuest pelo MCP. O token é digitado sem aparecer na tela (ou vem de JOBDRAGONQUEST_TOKEN), fica
// num arquivo com permissão 600 e a configuração do cliente guarda só o caminho desse arquivo, nunca o token.
//
//   node scripts/conectar-mcp.mjs --cliente claude-desktop
//   node scripts/conectar-mcp.mjs --cliente claude-code
//   node scripts/conectar-mcp.mjs --cliente cursor
//   node scripts/conectar-mcp.mjs --cliente json          (só mostra o bloco para colar em outro cliente)
// Opções: --api http://127.0.0.1:8080  --nome jobdragonquest  --token-arquivo <caminho>  --config <arquivo do cliente>
import { spawnSync } from "node:child_process";
import { chmodSync, copyFileSync, existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const SERVER = join(ROOT, "src", "jobdragonquest-mcp", "dist", "index.js");
const TOKEN = /^jdq_[A-Za-z0-9_-]{43}$/;
const CLIENTS = ["claude-desktop", "claude-code", "cursor", "json"];

function fail(message) {
  console.error(`ERRO: ${message}`);
  process.exit(1);
}

export function parseArgs(argv) {
  const options = { client: null, api: "http://127.0.0.1:8080", name: "jobdragonquest", tokenFile: join(homedir(), ".config", "jobdragonquest", "mcp-token"), config: null };
  const rest = [...argv];
  while (rest.length > 0) {
    const arg = rest.shift();
    const value = () => rest.shift() ?? fail(`informe o valor de ${arg}`);
    if (arg === "--cliente") options.client = value();
    else if (arg === "--api") options.api = value();
    else if (arg === "--nome") options.name = value();
    else if (arg === "--token-arquivo") options.tokenFile = resolve(value());
    else if (arg === "--config") options.config = resolve(value());
    else fail(`opção desconhecida: ${arg}`);
  }
  if (!CLIENTS.includes(options.client)) fail(`escolha --cliente ${CLIENTS.join(" | ")}`);
  if (!/^[a-z][a-z0-9-]{0,31}$/.test(options.name)) fail("--nome aceita letras minúsculas, números e hífen");
  return options;
}

/** Mesma regra do servidor MCP: só a própria máquina por HTTP; remoto só por HTTPS. */
export function validApi(raw) {
  try {
    const url = new URL(raw);
    const local = url.protocol === "http:" && ["127.0.0.1", "localhost", "[::1]"].includes(url.hostname);
    return !url.username && !url.password && !url.search && !url.hash && (local || url.protocol === "https:");
  } catch {
    return false;
  }
}

/** Caminho do arquivo de configuração de cada cliente, por sistema operacional. */
export function clientConfigPath(client, platform = process.platform, home = homedir(), env = process.env) {
  if (client === "cursor") return join(home, ".cursor", "mcp.json");
  if (platform === "darwin") return join(home, "Library", "Application Support", "Claude", "claude_desktop_config.json");
  if (platform === "win32") return join(env.APPDATA ?? join(home, "AppData", "Roaming"), "Claude", "claude_desktop_config.json");
  return join(home, ".config", "Claude", "claude_desktop_config.json");
}

function writePrivate(path, content) {
  mkdirSync(dirname(path), { recursive: true, mode: 0o700 });
  const temp = `${path}.${process.pid}.tmp`;
  writeFileSync(temp, content, { mode: 0o600 });
  chmodSync(temp, 0o600);
  renameSync(temp, path);
}

/** Junta o servidor ao arquivo do cliente sem apagar os outros. JSON inválido não é sobrescrito. */
export function mergeConfig(path, name, entry) {
  let config = {};
  if (existsSync(path)) {
    try {
      config = JSON.parse(readFileSync(path, "utf8"));
    } catch {
      fail(`${path} não é um JSON válido; corrija antes (nada foi alterado)`);
    }
    copyFileSync(path, `${path}.bak`);
    chmodSync(`${path}.bak`, 0o600);
  }
  if (typeof config !== "object" || config === null || Array.isArray(config)) fail(`${path} não tem um objeto JSON`);
  config.mcpServers = { ...(config.mcpServers ?? {}), [name]: entry };
  writePrivate(path, JSON.stringify(config, null, 2) + "\n");
}

async function askHidden(question) {
  if (!process.stdin.isTTY) fail("defina JOBDRAGONQUEST_TOKEN ou rode num terminal para digitar o token");
  process.stdout.write(question);
  return new Promise((resolvePromise) => {
    const stdin = process.stdin;
    let value = "";
    stdin.setRawMode(true);
    stdin.setEncoding("utf8");
    stdin.resume();
    const onData = (chunk) => {
      for (const char of chunk) {
        if (char === "\r" || char === "\n") {
          stdin.setRawMode(false);
          stdin.pause();
          stdin.off("data", onData);
          process.stdout.write("\n");
          resolvePromise(value.trim());
          return;
        }
        if (char === "\u0003") process.exit(130);
        value = char === "\u007f" || char === "\b" ? value.slice(0, -1) : value + char;
      }
    };
    stdin.on("data", onData);
  });
}

export async function main(argv) {
  const options = parseArgs(argv);
  if (!validApi(options.api)) fail("--api precisa ser http://127.0.0.1 (ou localhost) ou HTTPS");
  if (!existsSync(SERVER)) fail("servidor MCP não compilado: rode scripts/instalar.sh (ou npm ci && npm run build em src/jobdragonquest-mcp)");

  const token = process.env.JOBDRAGONQUEST_TOKEN?.trim() || (await askHidden("Cole o token do JobDragonQuest (não aparece na tela): "));
  if (!TOKEN.test(token)) fail("token em formato inválido: crie um em Configurações > Integrações e tokens de acesso");
  writePrivate(options.tokenFile, token + "\n");

  const env = { JOBDRAGONQUEST_TOKEN_FILE: options.tokenFile, JOBDRAGONQUEST_API_URL: options.api };
  // Caminho absoluto do Node: apps de desktop não herdam o PATH do terminal (nvm).
  const entry = { command: process.execPath, args: [SERVER], env };

  if (options.client === "json") {
    console.log(JSON.stringify({ mcpServers: { [options.name]: entry } }, null, 2));
  } else if (options.client === "claude-code") {
    const args = ["mcp", "add", "--scope", "user", "-e", `JOBDRAGONQUEST_TOKEN_FILE=${options.tokenFile}`, "-e", `JOBDRAGONQUEST_API_URL=${options.api}`, options.name, "--", process.execPath, SERVER];
    spawnSync("claude", ["mcp", "remove", "--scope", "user", options.name], { stdio: "ignore" });
    const result = spawnSync("claude", args, { stdio: "inherit" });
    if (result.error || result.status !== 0) {
      console.log("Não consegui rodar o comando claude. Rode você mesma:");
      console.log(`claude ${args.map((a) => (/\s/.test(a) ? JSON.stringify(a) : a)).join(" ")}`);
    }
  } else {
    const path = options.config ?? clientConfigPath(options.client);
    mergeConfig(path, options.name, entry);
    console.log(`Servidor "${options.name}" gravado em ${path} (cópia anterior em .bak). Feche e abra o ${options.client === "cursor" ? "Cursor" : "Claude Desktop"}.`);
  }
  console.log(`Token guardado em ${options.tokenFile} (permissão 600). Para cortar o acesso, revogue o token no painel.`);
  console.log("MCP_CONECTADO_OK");
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main(process.argv.slice(2));
}
