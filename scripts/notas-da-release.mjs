#!/usr/bin/env node
// Extrai do CHANGELOG.md a seção de uma versão, para virar as notas da página de Release (workflow release.yml).
// Uso: node scripts/notas-da-release.mjs v0.5.0 > notas.md   (falha se a versão não estiver no changelog)
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

export function notes(changelog, tag) {
  const version = String(tag).replace(/^v/, "");
  if (!/^\d+\.\d+\.\d+$/.test(version)) throw new Error(`Versão inválida: ${tag}`);
  const start = changelog.indexOf(`## [${version}] - `);
  if (start < 0) throw new Error(`A versão ${version} não está no CHANGELOG.md`);
  const body = changelog.slice(changelog.indexOf("\n", start) + 1);
  const end = body.search(/^## \[|^\[[^\]]+\]: /m);
  const text = (end < 0 ? body : body.slice(0, end)).trim();
  if (!text) throw new Error(`A seção ${version} do CHANGELOG.md está vazia`);
  return `Queime as etapas da sua procura por vagas.\n\n${text}\n\nInstalação em um comando: veja o README. Histórico completo no CHANGELOG.md.\n`;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const root = join(dirname(fileURLToPath(import.meta.url)), "..");
  process.stdout.write(notes(readFileSync(join(root, "CHANGELOG.md"), "utf8"), process.argv[2]));
}
