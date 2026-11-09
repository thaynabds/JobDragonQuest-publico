// Confere as mensagens de commit: Conventional Commits citando a issue, sem crédito a IA.
// Uso: node scripts/verificar/commits.mjs [faixa]   (padrão: origin/develop..HEAD). Commits de merge ficam de fora.
import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

export const TYPES = ["feat", "fix", "docs", "test", "refactor", "perf", "ci", "build", "chore", "style", "revert"];
const HEADER = new RegExp(`^(${TYPES.join("|")})(\\(([a-z0-9-]+)\\))?(!)?: (\\S.*)$`);
const ISSUES = / \(#\d+(, #\d+)*\)$/;
export const MAX_SUBJECT = 130;
// Montado em partes para este arquivo não casar consigo mesmo na auditoria de crédito.
const AI_CREDIT = new RegExp(["co-authored-by:\\s*(claude|copilot|chatgpt|gemini)", "generated with \\[?claude", "noreply@" + "anthropic"].join("|"), "i");

export const BIG_FILES = 30;
export const BIG_LINES = 800;

/** Aviso (não erro) para commit grande demais: provavelmente junta mudanças que deveriam ser commits separados. */
export function sizeWarning(files, lines) {
  if (files > BIG_FILES || lines > BIG_LINES) {
    return `commit grande (${files} arquivos, ${lines} linhas): se ele junta mudanças diferentes, separe em commits menores`;
  }
  return null;
}

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
  if (process.argv[2] === "--arquivo") {
    // Modo do hook commit-msg: confere a mensagem que está sendo escrita, ignorando as linhas de comentário do Git.
    const message = readFileSync(process.argv[3], "utf8").split("\n").filter((line) => !line.startsWith("#")).join("\n").trim();
    if (/^(Merge|Revert) /.test(message)) process.exit(0);
    const found = problems(message);
    if (found.length > 0) {
      console.error(`Mensagem de commit fora do padrão:\n  ${found.join("\n  ")}\nVeja CONTRIBUTING.md.`);
      process.exit(1);
    }
    console.log("COMMITS_OK");
    process.exit(0);
  }
  const range = process.argv[2] ?? "origin/develop..HEAD";
  // A faixa vem do CI ou de quem roda; só aceita formas de referência do Git, nunca opção de linha de comando.
  if (!/^[A-Za-z0-9._\/-]+(\.\.\.?[A-Za-z0-9._\/-]+)?$/.test(range) || range.startsWith("-")) {
    console.error(`Faixa inválida: ${range}`);
    process.exit(2);
  }
  // O que já está publicado na main não é deste PR (acontece quando a main volta para a develop).
  const hasMain = spawnSync("git", ["rev-parse", "--verify", "-q", "origin/main"]).status === 0;
  const log = git(["log", "--no-merges", "--format=%H%x1f%B%x1e", range, ...(hasMain && !range.includes("origin/main") ? ["^origin/main"] : [])]);
  const commits = log.split("\x1e").map((entry) => entry.trim()).filter(Boolean).map((entry) => entry.split("\x1f"));
  let bad = 0;
  for (const [sha, message] of commits) {
    const stat = git(["show", "--numstat", "--format=", sha]).split("\n").filter(Boolean);
    const lines = stat.reduce((sum, row) => sum + (Number(row.split("\t")[0]) || 0) + (Number(row.split("\t")[1]) || 0), 0);
    const warning = sizeWarning(stat.length, lines);
    if (warning) {
      // No GitHub Actions vira um aviso amarelo no PR; não reprova o CI.
      console.log(`${process.env.GITHUB_ACTIONS === "true" ? "::warning::" : "AVISO   "}${sha.slice(0, 7)} ${warning}`);
    }
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
