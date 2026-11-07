// G46: o quadro permite selecionar várias candidaturas e mudar a etapa ou descartar todas de uma vez,
// com isolamento entre contas, testes na API e no painel.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const api = join(ROOT, "src", "jobdragonquest-api");
const web = join(ROOT, "src", "jobdragonquest-web");
const controller = readFileSync(join(api, "src/main/java/dev/jobdragonquest/applications/ApplicationController.java"), "utf8");
check("a API tem a rota de mudança em lote", controller.includes('@PostMapping("/batch/transitions")'));
check("o lote grava como mudança manual da pessoa", /EventSource\.MANUAL/.test(controller));
const dtos = readFileSync(join(api, "src/main/java/dev/jobdragonquest/applications/ApplicationDtos.java"), "utf8");
check("o lote é limitado a 100 candidaturas", /@NotEmpty @Size\(max = 100\) List<@NotNull UUID> ids/.test(dtos));
const board = readFileSync(join(web, "src/pages/BoardPage.tsx"), "utf8");
check("o quadro tem seleção e barra de ações", board.includes("api.batchTransition") && board.includes("batch-bar"));
for (const locale of ["pt-BR", "en", "es"]) {
  const text = readFileSync(join(web, "src/i18n/locales", `${locale}.ts`), "utf8");
  check(`textos do lote em ${locale}`, ["board.batchApply", "board.batchDiscard", "board.selected"].every((key) => text.includes(`"${key}"`)));
}
const apiTests = run("./mvnw", ["-q", "-B", "-Djacoco.skip=true", "-Dtest=ApplicationBatchTest", "-Dsurefire.failIfNoSpecifiedTests=true", "test"],
  { cwd: api, timeout: 1_200_000 });
check("testes do lote na API passam", apiTests.status === 0, (apiTests.stdout + apiTests.stderr).slice(-400));
const webTests = run("npx", ["vitest", "run", "src/pages/BoardPage.test.tsx"], { cwd: web, timeout: 600_000 });
check("testes do quadro no painel passam", webTests.status === 0, webTests.stdout.slice(-400));
finish("LOTE_OK");
