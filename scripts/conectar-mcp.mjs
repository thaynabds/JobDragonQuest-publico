#!/usr/bin/env node
// Liga um cliente de IA ao JobDragonQuest pelo MCP. O token é digitado sem aparecer na tela (ou vem de JOBDRAGONQUEST_TOKEN), fica
// num arquivo com permissão 600 e a configuração do cliente guarda só o caminho desse arquivo, nunca o token.
//
//   node scripts/conectar-mcp.mjs --cliente claude-desktop
//   node scripts/conectar-mcp.mjs --cliente claude-code
//   node scripts/conectar-mcp.mjs --cliente cursor
//   node scripts/conectar-mcp.mjs --cliente json          (só mostra o bloco para colar em outro cliente)
// Opções: --api http://127.0.0.1:8080  --nome jobdragonquest  --token-arquivo <caminho>  --config <arquivo do cliente>
//         --sem-testar-token   (não confere o token na API antes de salvar; usado nos testes automáticos)
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
  const options = { client: null, api: "http://127.0.0.1:8080", name: "jobdragonquest", tokenFile: join(homedir(), ".config", "jobdragonquest", "mcp-token"), config: null, checkToken: true };
  const rest = [...argv];
  while (rest.length > 0) {
    const arg = rest.shift();
    const value = () => rest.shift() ?? fail(`informe o valor de ${arg}`);
    if (arg === "--cliente") options.client = value();
    else if (arg === "--api") options.api = value();
    else if (arg === "--nome") options.name = value();
    else if (arg === "--token-arquivo") options.tokenFile = resolve(value());
    else if (arg === "--config") options.config = resolve(value());
    else if (arg === "--sem-testar-token") options.checkToken = false;
    else fail(`opção desconhecida: ${arg}`);
  }
  if (!CLIENTS.includes(options.client)) fail(`escolha --cliente ${CLIENTS.join(" | ")}`);
  if (!/^[a-z][a-z0-9-]{0,31}$/.test(options.name)) fail("--nome aceita letras minúsculas, números e hífen");
  return options;
}

/**
 * Limpa o token colado no terminal: marcadores de colagem (ESC[200~ e ESC[201~), outras sequências de controle,
 * espaços, aspas e um "Bearer " na frente. Era isso que fazia um token certo ser recusado.
 */
export function cleanToken(raw) {
  return String(raw ?? "")
    .replace(/\u001b\[[0-9;]*[~A-Za-z]/g, "")
    .replace(/[\u0000-\u001f\u007f]/g, "")
    .trim()
    .replace(/^["'`]+|["'`]+$/g, "")
    .replace(/^Bearer\s+/i, "")
    .trim();
}

/** Explica o que está errado no token sem mostrar o valor. */
export function tokenProblem(token) {
  if (!token) return "nenhum token foi colado";
  if (!token.startsWith("jdq_")) return "o token precisa começar com jdq_ (copie o token inteiro, não o nome dele)";
  if (token.length !== 47) return `o token tem ${token.length} caracteres e deveria ter 47 (copie de novo, inteiro)`;
  return "o token tem caracteres que não deveria ter (copie de novo direto do painel)";
}

/** Confere o token na API local. Devolve "ok", "recusado" ou "sem-api" (API desligada ou fora do ar). */
export async function checkTokenOnApi(api, token, fetchImpl = fetch) {
  try {
    const response = await fetchImpl(`${api}/api/v1/dashboard/summary`, {
      headers: { Authorization: `Bearer ${token}` },
      redirect: "error",
      signal: AbortSignal.timeout(5000),
    });
    if (response.status === 401 || response.status === 403) return "recusado";
    return response.ok ? "ok" : "sem-api";
  } catch {
    return "sem-api";
  }
}

/**
 * Comando de registro no Claude Code. Usa "claude mcp add-json" com a configuração inteira num argumento só: o
 * "claude mcp add -e A=1 -e B=2 nome" quebrava porque o -e aceita vários valores e engolia o nome.
 */
export function claudeCodeArgs(name, entry) {
  return ["mcp", "add-json", "--scope", "user", name, JSON.stringify({ type: "stdio", ...entry })];
}

/** O mesmo comando pronto para colar no terminal (aspas simples no JSON). */
export function shellCommand(args) {
  return ["claude", ...args].map((a) => (/^[A-Za-z0-9_./:=-]+$/.test(a) ? a : `'${a.replace(/'/g, "'\\''")}'`)).join(" ");
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

  const typed = process.env.JOBDRAGONQUEST_TOKEN || (await askHidden("Cole o token do JobDragonQuest (não aparece na tela) e aperte Enter: "));
  const token = cleanToken(typed);
  if (!TOKEN.test(token)) fail(`token em formato inválido: ${tokenProblem(token)}. Crie um em Configurações > Integrações e tokens de acesso. Não cole o token direto no terminal.`);
  if (options.checkToken) {
    const status = await checkTokenOnApi(options.api, token);
    if (status === "recusado") fail("a API recusou o token: ele foi revogado ou é de outra instalação. Crie um token novo no painel.");
    if (status === "sem-api") console.log(`Aviso: não consegui falar com a API em ${options.api} para conferir o token. Suba o JobDragonQuest com scripts/iniciar.sh.`);
  }
  writePrivate(options.tokenFile, token + "\n");

  const env = { JOBDRAGONQUEST_TOKEN_FILE: options.tokenFile, JOBDRAGONQUEST_API_URL: options.api };
  // Caminho absoluto do Node: apps de desktop não herdam o PATH do terminal (nvm).
  const entry = { command: process.execPath, args: [SERVER], env };

  if (options.client === "json") {
    console.log(JSON.stringify({ mcpServers: { [options.name]: entry } }, null, 2));
  } else if (options.client === "claude-code") {
    const args = claudeCodeArgs(options.name, entry);
    spawnSync("claude", ["mcp", "remove", "--scope", "user", options.name], { stdio: "ignore" });
    const result = spawnSync("claude", args, { stdio: "inherit" });
    const confirmed = !result.error && result.status === 0 && spawnSync("claude", ["mcp", "get", options.name], { stdio: "ignore" }).status === 0;
    if (!confirmed) {
      console.error("Não consegui registrar no Claude Code. O token já está salvo; rode este comando no terminal:");
      console.error(shellCommand(args));
      process.exit(1);
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
