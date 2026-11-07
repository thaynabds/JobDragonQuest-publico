#!/usr/bin/env node
// Soma relatórios JUnit (Surefire, Vitest) e imprime uma linha de resumo; no GitHub Actions também a escreve no resumo
// da execução. Falha se não achar nenhum relatório, para o número publicado nunca ser inventado.
// Uso: node scripts/resumo-testes.mjs "<nome>" <pasta-ou-arquivo> [...]
import { appendFileSync, existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

export function count(xml) {
  const attribute = (tag, name) => Number((tag.match(new RegExp(`\\s${name}="(\\d+)"`)) ?? [])[1] ?? 0);
  const root = xml.match(/<testsuites\b[^>]*>/);
  const tags = root && /\stests="\d+"/.test(root[0]) ? [root[0]] : xml.match(/<testsuite\b[^>]*>/g) ?? [];
  return tags.reduce((total, tag) => ({
    tests: total.tests + attribute(tag, "tests"),
    failures: total.failures + attribute(tag, "failures") + attribute(tag, "errors"),
    skipped: total.skipped + attribute(tag, "skipped"),
  }), { tests: 0, failures: 0, skipped: 0 });
}

function files(path) {
  if (!existsSync(path)) return [];
  if (statSync(path).isFile()) return [path];
  return readdirSync(path).filter((name) => /\.xml$/.test(name)).map((name) => join(path, name));
}

const [name, ...paths] = process.argv.slice(2);
if (!name || paths.length === 0) {
  console.error('Uso: node scripts/resumo-testes.mjs "<nome>" <pasta-ou-arquivo> [...]');
  process.exit(2);
}
const reports = paths.flatMap(files);
const total = reports.map((file) => count(readFileSync(file, "utf8")))
  .reduce((a, b) => ({ tests: a.tests + b.tests, failures: a.failures + b.failures, skipped: a.skipped + b.skipped }), { tests: 0, failures: 0, skipped: 0 });
const line = `${name}: ${total.tests} testes, ${total.failures} falhas, ${total.skipped} ignorados (${reports.length} relatórios)`;
console.log(line);
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `- ${line}\n`);
process.exit(reports.length > 0 && total.tests > 0 ? 0 : 1);
