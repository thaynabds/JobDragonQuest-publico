#!/usr/bin/env node
// Exporta o histórico de issues e PRs do repositório principal (privado) para docs/historico-issues-prs.md, que vai
// para o espelho público como evidência auditável do processo. Só exporta campos de processo: número, título, estado,
// datas, branches e issues fechadas. Nunca exporta corpo de issue, comentário, autor ou e-mail.
//
// Uso: node scripts/exportar-historico.mjs [--stdout]
import { spawnSync } from "node:child_process";
import { writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
export const REPO = "thaynabds/JobDragonQuest";
const OUT = join(ROOT, "docs", "historico-issues-prs.md");

function gh(args) {
  const result = spawnSync("gh", args, { cwd: ROOT, encoding: "utf8", timeout: 120_000, maxBuffer: 32 * 1024 * 1024 });
  if (result.status !== 0) throw new Error(`gh ${args.slice(0, 2).join(" ")} falhou: ${(result.stderr ?? "").trim()}`);
  return JSON.parse(result.stdout);
}

/** Texto de uma célula de tabela Markdown: sem quebra de linha, sem barra vertical, sem HTML. */
export function cell(value) {
  return String(value ?? "").replace(/[\r\n]+/g, " ").replace(/\|/g, "\\|").replace(/</g, "&lt;").replace(/>/g, "&gt;").trim();
}

/** Números das issues que o corpo do PR fecha (Closes/Fixes/Resolves #NN). */
export function closedIssues(body) {
  const found = new Set();
  for (const match of String(body ?? "").matchAll(/\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s+#(\d+)/gi)) found.add(Number(match[1]));
  return [...found].sort((a, b) => a - b);
}

const day = (iso) => (iso ? iso.slice(0, 10) : "");

export function loadHistory() {
  const issues = gh(["issue", "list", "-R", REPO, "-s", "all", "-L", "1000", "--json", "number,title,state,createdAt,closedAt,labels"])
    .sort((a, b) => a.number - b.number);
  const pulls = gh(["pr", "list", "-R", REPO, "-s", "all", "-L", "1000", "--json", "number,title,state,createdAt,mergedAt,baseRefName,headRefName,body,author"])
    .sort((a, b) => a.number - b.number)
    .map((pr) => ({ ...pr, closes: closedIssues(pr.body), body: undefined, author: { login: pr.author?.is_bot || /dependabot/.test(pr.author?.login ?? "") ? "dependabot" : "pessoa" } }));
  return { issues, pulls };
}

export function render({ issues, pulls }) {
  const fixedBy = new Map();
  for (const pr of pulls) for (const issue of pr.closes) fixedBy.set(issue, [...(fixedBy.get(issue) ?? []), pr.number]);
  const merged = pulls.filter((pr) => pr.state === "MERGED").length;
  const lines = [
    "# Histórico de issues e PRs",
    "",
    `Exportado do repositório principal (\`${REPO}\`, privado) por \`node scripts/exportar-historico.mjs\`. O espelho`,
    "público tem o mesmo código e as mesmas tags, mas o GitHub não copia issues nem PRs entre repositórios; esta página é",
    "a evidência auditável do processo. Só campos de processo são exportados (sem corpo, comentário ou autor).",
    "Por que existem dois repositórios: [governanca.md](governanca.md).",
    "",
    `Resumo: ${issues.length} issues (${issues.filter((i) => i.state === "CLOSED").length} fechadas) e ${pulls.length} PRs (${merged} com merge).`,
    "",
    "## Issues",
    "",
    "| # | Título | Estado | Aberta | Fechada | PR que fechou | Labels |",
    "|---|---|---|---|---|---|---|",
    ...issues.map((issue) => `| ${issue.number} | ${cell(issue.title)} | ${issue.state} | ${day(issue.createdAt)} | ${day(issue.closedAt)} | ${(fixedBy.get(issue.number) ?? []).map((n) => `PR ${n}`).join(", ")} | ${cell(issue.labels.map((l) => l.name).join(", "))} |`),
    "",
    "## Pull requests",
    "",
    "| # | Título | Estado | Origem → destino | Aberto | Merge | Fecha |",
    "|---|---|---|---|---|---|---|",
    ...pulls.map((pr) => `| ${pr.number} | ${cell(pr.title)} | ${pr.state} | \`${cell(pr.headRefName)}\` → \`${cell(pr.baseRefName)}\` | ${day(pr.createdAt)} | ${day(pr.mergedAt)} | ${pr.closes.map((n) => `#${n}`).join(", ")} |`),
    "",
  ];
  return lines.join("\n");
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const text = render(loadHistory());
  if (process.argv.includes("--stdout")) process.stdout.write(text);
  else {
    writeFileSync(OUT, text);
    console.log(`Histórico gravado em ${OUT}`);
  }
}
