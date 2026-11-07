// Confere as mensagens de commit: Conventional Commits citando a issue, sem crédito a IA.
// Uso: node scripts/verificar/commits.mjs [faixa]   (padrão: origin/develop..HEAD). Commits de merge ficam de fora.
import { spawnSync } from "node:child_process";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

export const TYPES = ["feat", "fix", "docs", "test", "refactor", "perf", "ci", "build", "chore", "style", "revert"];
const HEADER = new RegExp(`^(${TYPES.join("|")})(\\(([a-z0-9-]+)\\))?(!)?: (\\S.*)$`);
const ISSUES = / \(#\d+(, #\d+)*\)$/;
export const MAX_SUBJECT = 130;
// Montado em partes para este arquivo não casar consigo mesmo na auditoria de crédito.
const AI_CREDIT = new RegExp(["co-authored-by:\\s*(claude|copilot|chatgpt|gemini)", "generated with \\[?claude", "noreply@" + "anthropic"].join("|"), "i");

/** Devolve a lista de problemas de uma mensagem de commit (vazia quando a mensagem está boa). */
export function problems(message) {
  const [subject = "", ...rest] = String(message).replace(/\r/g, "").split("\n");
  const body = rest.join("\n");
  const found = [];
  const header = subject.match(HEADER);
  if (!header) {
    found.push(`cabeçalho fora do padrão tipo(escopo): descrição (tipos: ${TYPES.join(", ")})`);
  } else {
    const [, type, , scope, , description] = header;
    const release = type === "chore" && scope === "release" && /^versão \d+\.\d+\.\d+$/.test(description);
    const dependency = type === "chore" && (scope === "deps" || scope === "ci") && /^bump /.test(description);
    if (!release && !dependency && !ISSUES.test(subject)) found.push("falta citar a issue no fim, como (#12) ou (#12, #13)");
    if (/\.\s*(\(#\d+(, #\d+)*\))?$/.test(subject)) found.push("descrição termina com ponto");
  }
  if (subject.length > MAX_SUBJECT) found.push(`cabeçalho com ${subject.length} caracteres (máximo ${MAX_SUBJECT})`);
  if (rest.length > 0 && rest[0].trim() !== "") found.push("falta a linha em branco entre o cabeçalho e o corpo");
  if (AI_CREDIT.test(body) || AI_CREDIT.test(subject)) found.push("mensagem dá crédito a IA");
  return found;
}

function git(args) {
  const result = spawnSync("git", args, { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
  if (result.status !== 0) throw new Error(`git ${args.join(" ")}: ${(result.stderr ?? "").trim()}`);
  return result.stdout;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const range = process.argv[2] ?? "origin/develop..HEAD";
  // A faixa vem do CI ou de quem roda; só aceita formas de referência do Git, nunca opção de linha de comando.
  if (!/^[A-Za-z0-9._\/-]+(\.\.\.?[A-Za-z0-9._\/-]+)?$/.test(range) || range.startsWith("-")) {
    console.error(`Faixa inválida: ${range}`);
    process.exit(2);
  }
  const log = git(["log", "--no-merges", "--format=%H%x1f%B%x1e", range]);
  const commits = log.split("\x1e").map((entry) => entry.trim()).filter(Boolean).map((entry) => entry.split("\x1f"));
  let bad = 0;
  for (const [sha, message] of commits) {
    const found = problems(message.trim());
    if (found.length > 0) {
      bad += 1;
      console.log(`FALHOU  ${sha.slice(0, 7)} ${message.split("\n")[0]}\n        ${found.join("\n        ")}`);
    }
  }
  if (bad > 0) {
    console.log(`\n${bad} de ${commits.length} commit(s) fora do padrão. Veja CONTRIBUTING.md.`);
    process.exit(1);
  }
  console.log(`${commits.length} commit(s) conferido(s) em ${range}.`);
  console.log("COMMITS_OK");
}
