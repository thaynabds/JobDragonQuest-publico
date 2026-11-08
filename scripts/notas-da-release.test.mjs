// Testes do extrator de notas de release (node --test).
import assert from "node:assert/strict";
import { test } from "node:test";

import { notes } from "./notas-da-release.mjs";

const changelog = "# Changelog\n\n## [Não lançado]\n\n## [1.2.0] - 2026-11-07\n\n### Adicionado\n- coisa nova (#9).\n\n## [1.1.0] - 2026-11-01\n\n- antiga\n\n[1.2.0]: https://x\n";

test("pega só a seção da versão pedida", () => {
  const text = notes(changelog, "v1.2.0");
  assert.match(text, /coisa nova/);
  assert.doesNotMatch(text, /antiga/);
});

test("a última versão termina antes dos links", () => {
  assert.doesNotMatch(notes(changelog, "1.1.0"), /https:\/\/x/);
});

test("falha com versão ausente ou inválida", () => {
  assert.throws(() => notes(changelog, "v9.9.9"), /não está/);
  assert.throws(() => notes(changelog, "v1.2.0; rm -rf /"), /inválida/);
});
