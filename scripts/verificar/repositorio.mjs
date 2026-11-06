// G11: tudo publicado no repositório privado, árvore limpa, sem segredo versionado e sem crédito de IA.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

// Padrões montados em partes para este arquivo não casar consigo mesmo.
const AI_CREDIT = new RegExp(["co-authored-by:\\s*claude", "generated with \\[?claude", "noreply@" + "anthropic", "claude\\.com/claude-code",
  "feito com (ia|claude)", "gerado (com|por) (ia|claude)"].join("|"), "i");
const SECRET = new RegExp(["sk-" + "ant-", "gh" + "p_[A-Za-z0-9]{20}", "-----BEGIN [A-Z ]*PRIVATE" + " KEY", "pk_" + "live_",
  "AKIA[0-9A-Z]{16}", "xox[bp]-[0-9]"].join("|"));

const status = run("git", ["status", "--porcelain"]);
check("árvore de trabalho limpa", status.status === 0 && status.stdout.trim() === "", status.stdout);

run("git", ["fetch", "-q", "origin", "--tags"]);
for (const branch of ["main", "develop"]) {
  const local = run("git", ["rev-parse", branch]).stdout.trim();
  const remote = run("git", ["rev-parse", `origin/${branch}`]).stdout.trim();
  check(`${branch} publicada (local = origin)`, local !== "" && local === remote, `${local} ${remote}`);
}

const repo = run("gh", ["repo", "view", "thaynabds/JobFly", "--json", "visibility", "-q", ".visibility"]);
check("repositório é privado", repo.stdout.trim() === "PRIVATE", repo.stdout || repo.stderr);

const messages = run("git", ["log", "--all", "--format=%H%n%B%n%an <%ae>%n%cn <%ce>"]);
const credited = messages.stdout.split("\n").filter((line) => AI_CREDIT.test(line));
check("nenhum commit dá crédito a IA", messages.status === 0 && credited.length === 0, credited.slice(0, 3).join(" | "));

const files = run("git", ["ls-files"]).stdout.split("\n").filter(Boolean);
check("nenhum .env, chave ou dado local versionado",
  !files.some((file) => /(^|\/)\.env$|(^|\/)data\/|\.pem$|\.key$|admin-password|GATES\.md$|settings\.local\.json$/.test(file)),
  files.filter((file) => /\.env$|data\//.test(file)).join(", "));

const textFiles = files.filter((file) => !/\.(png|jpe?g|gif|pdf|jar|ico|woff2?)$|package-lock\.json$|mvnw(\.cmd)?$/.test(file));
const creditFiles = [];
const secretFiles = [];
for (const file of textFiles) {
  let content = "";
  try {
    content = readFileSync(join(ROOT, file), "utf8");
  } catch {
    continue;
  }
  if (AI_CREDIT.test(content)) creditFiles.push(file);
  if (SECRET.test(content)) secretFiles.push(file);
}
check(`nenhum arquivo versionado dá crédito a IA (${textFiles.length} conferidos)`, creditFiles.length === 0, creditFiles.join(", "));
check("nenhum segredo conhecido em arquivo versionado", secretFiles.length === 0, secretFiles.join(", "));
finish("REPOSITORIO_OK");
