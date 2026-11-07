#!/usr/bin/env node
// Cria ou atualiza o conjunto padrão de labels (.github/labels.json) num repositório do GitHub. Não apaga nenhuma label.
// Uso: node scripts/criar-labels.mjs thaynabds/JobDragonQuest [thaynabds/JobDragonQuest-publico ...]
import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const labels = JSON.parse(readFileSync(join(ROOT, ".github", "labels.json"), "utf8"));
const repos = process.argv.slice(2);
if (repos.length === 0 || repos.some((repo) => !/^[A-Za-z0-9-]+\/[A-Za-z0-9._-]+$/.test(repo))) {
  console.error("Uso: node scripts/criar-labels.mjs dono/repositorio [...]");
  process.exit(1);
}
let failed = 0;
for (const repo of repos) {
  for (const label of labels) {
    const result = spawnSync("gh", ["label", "create", label.name, "-R", repo, "--color", label.color, "--description", label.description, "--force"],
      { encoding: "utf8", timeout: 60_000 });
    if (result.status !== 0) {
      failed += 1;
      console.error(`${repo}: ${label.name}: ${(result.stderr ?? "").trim()}`);
    }
  }
  console.log(`${repo}: ${labels.length} labels conferidas`);
}
process.exit(failed === 0 ? 0 : 1);
