// Configuração lida das variáveis de ambiente. O token nunca é impresso, nem em erro.
import { readFileSync, statSync } from "node:fs";

export interface Config {
  apiUrl: string;
  token: string;
}

export class ConfigError extends Error {}

const TOKEN = /^jfp_[A-Za-z0-9_-]{43}$/;
const LOOPBACK = new Set(["127.0.0.1", "localhost", "[::1]"]);

/**
 * JOBFLY_API_URL: padrão http://127.0.0.1:8080. Só aceita a própria máquina por HTTP; um servidor remoto exige HTTPS
 * e JOBFLY_ALLOW_REMOTE=1, para um prompt malicioso não conseguir mandar o token para outro lugar.
 * JOBFLY_TOKEN: token de acesso pessoal criado em Configurações no painel (jfp_…).
 * JOBFLY_TOKEN_FILE: alternativa recomendada, caminho de um arquivo com o token (permissão 600). Assim a configuração
 * do cliente de IA guarda só o caminho, nunca o token.
 */
export function loadConfig(env: NodeJS.ProcessEnv): Config {
  const raw = env.JOBFLY_API_URL?.trim() || "http://127.0.0.1:8080";
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    throw new ConfigError("JOBFLY_API_URL inválida");
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new ConfigError("JOBFLY_API_URL não pode ter usuário, senha, query ou fragmento");
  }
  const local = url.protocol === "http:" && LOOPBACK.has(url.hostname);
  const remote = url.protocol === "https:" && env.JOBFLY_ALLOW_REMOTE === "1";
  if (!local && !remote) {
    throw new ConfigError("JOBFLY_API_URL precisa ser http://127.0.0.1 (ou localhost), ou HTTPS com JOBFLY_ALLOW_REMOTE=1");
  }
  const token = env.JOBFLY_TOKEN?.trim() || readTokenFile(env.JOBFLY_TOKEN_FILE);
  if (!TOKEN.test(token)) {
    throw new ConfigError("JOBFLY_TOKEN ausente ou em formato inválido: crie um token em Configurações no painel do JobFly");
  }
  return { apiUrl: url.origin + url.pathname.replace(/\/+$/, ""), token };
}

/** Lê o token de um arquivo pequeno, recusando arquivo que outras contas possam ler (em sistemas POSIX). */
export function readTokenFile(path: string | undefined): string {
  if (!path) {
    return "";
  }
  let info;
  try {
    info = statSync(path);
  } catch {
    throw new ConfigError("JOBFLY_TOKEN_FILE não encontrado");
  }
  if (!info.isFile() || info.size > 256) {
    throw new ConfigError("JOBFLY_TOKEN_FILE precisa ser um arquivo pequeno com só o token");
  }
  if (process.platform !== "win32" && (info.mode & 0o077) !== 0) {
    throw new ConfigError("JOBFLY_TOKEN_FILE pode ser lido por outras contas: use chmod 600");
  }
  return readFileSync(path, "utf8").trim();
}

