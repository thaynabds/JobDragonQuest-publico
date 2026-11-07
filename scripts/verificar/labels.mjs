// G7: o repositório principal e o espelho público têm o conjunto padrão de labels de .github/labels.json.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const expected = JSON.parse(readFileSync(join(ROOT, ".github", "labels.json"), "utf8"));
check("conjunto padrão tem 23 labels com nome, cor e descrição", expected.length === 23
  && expected.every((label) => /^[a-z]+:[a-z-]+$/.test(label.name) && /^[0-9a-f]{6}$/.test(label.color) && label.description));
for (const repo of ["thaynabds/JobDragonQuest", "thaynabds/JobDragonQuest-publico"]) {
  const result = run("gh", ["label", "list", "-R", repo, "-L", "200", "--json", "name"]);
  const names = new Set(result.status === 0 ? JSON.parse(result.stdout).map((label) => label.name) : []);
  const missing = expected.filter((label) => !names.has(label.name)).map((label) => label.name);
  check(`${repo} tem as ${expected.length} labels padrão`, result.status === 0 && missing.length === 0, missing.join(", ") || result.stderr);
}
finish("LABELS_OK");
