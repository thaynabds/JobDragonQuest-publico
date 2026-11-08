// Testes do verificador de mensagens de commit (node --test).
import assert from "node:assert/strict";
import { test } from "node:test";

import { MAX_SUBJECT, problems } from "./commits.mjs";

test("aceita mensagens no padrão do projeto", () => {
  for (const message of [
    "feat(sources): cadastrar fontes vigiadas em lote para vários perfis (#9)",
    "fix: corrige a ordem do -e no conectar-mcp (#119)",
    "feat: vaga pelo link, importação por CSV e extensão do navegador (#114, #115, #116)",
    "feat(api)!: muda o formato do erro (#131)\n\nBREAKING CHANGE: o campo type sai do corpo.",
    "chore(release): versão 0.4.1",
    "chore(deps): bump vite from 7.3.6 to 7.3.7 in /src/jobdragonquest-web",
    "docs: README com prompt pronto para IA (#54)\n\nCorpo explicando o porquê.",
  ]) {
    assert.deepEqual(problems(message), [], message);
  }
});

test("recusa cabeçalho fora do padrão", () => {
  assert.match(problems("Atualiza coisas")[0], /cabeçalho fora do padrão/);
  assert.match(problems("feature: algo novo (#1)")[0], /cabeçalho fora do padrão/);
  assert.match(problems("feat(Web): algo (#1)")[0], /cabeçalho fora do padrão/);
  assert.match(problems("feat:sem espaço (#1)")[0], /cabeçalho fora do padrão/);
});

test("exige a issue, menos na release e nas atualizações do Dependabot", () => {
  assert.ok(problems("feat(web): tela nova").some((p) => /citar a issue/.test(p)));
  assert.ok(problems("chore(release): preparar tudo").some((p) => /citar a issue/.test(p)));
  assert.ok(problems("chore(deps): atualizar tudo").some((p) => /citar a issue/.test(p)));
});

test("recusa ponto final, cabeçalho longo e corpo colado, e aceita nome próprio no começo", () => {
  assert.deepEqual(problems("fix(scripts): PostgreSQL local sobe depois de mover a pasta (#87)"), []);
  assert.ok(problems("feat: tela nova. (#1)").some((p) => /ponto/.test(p)));
  assert.ok(problems(`feat: ${"x".repeat(MAX_SUBJECT)} (#1)`).some((p) => /caracteres/.test(p)));
  assert.ok(problems("feat: tela nova (#1)\ncorpo sem linha em branco").some((p) => /linha em branco/.test(p)));
});

test("recusa crédito a IA no corpo", () => {
  // Montado em partes para o arquivo não conter a frase proibida (a auditoria do repositório recusaria).
  const credit = ["Co-Authored", "-By: ", "Cla", "ude <x@y>"].join("");
  assert.ok(problems(`feat: tela nova (#1)\n\n${credit}`).some((p) => /crédito a IA/.test(p)));
});
