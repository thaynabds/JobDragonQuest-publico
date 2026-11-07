// G43: cada perfil escolhe o modo de automação (só guardar, fila de revisão ou preparar) e a fila de revisão aprova
// ou descarta. Nenhum modo envia candidatura sozinho. Testes na API e no painel.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const api = join(ROOT, "src", "jobdragonquest-api");
const web = join(ROOT, "src", "jobdragonquest-web");
const main = join(api, "src/main/java/dev/jobdragonquest");
check("migração do modo com padrão REVIEW", /automation_mode varchar\(20\) not null default 'REVIEW'/.test(
  readFileSync(join(api, "src/main/resources/db/migration/V9__modo_automacao.sql"), "utf8")));
const collector = readFileSync(join(main, "sources/SourceCollector.java"), "utf8");
check("a coleta respeita OFF, REVIEW e AUTOMATIC", collector.includes("AutomationMode.OFF") && collector.includes("ApplicationState.PREPARED")
  && collector.includes("ApplicationState.RECOMMENDED"));
check("nenhum modo cria candidatura enviada", !/ApplicationState\.SUBMITTED/.test(collector));
const page = readFileSync(join(web, "src/pages/ReviewQueuePage.tsx"), "utf8");
check("a fila aprova para preparada e descarta para encerrada", page.includes('"PREPARED"') && page.includes('"CLOSED"'));
check("a fila está no menu", readFileSync(join(web, "src/components/Layout.tsx"), "utf8").includes('"/revisao"'));
for (const locale of ["pt-BR", "en", "es"]) {
  const text = readFileSync(join(web, "src/i18n/locales", `${locale}.ts`), "utf8");
  check(`textos da automação em ${locale}`, ["review.title", "automationMode.AUTOMATIC", "profiles.automationHint.OFF"].every((key) => text.includes(`"${key}"`)));
}
const apiTests = run("./mvnw", ["-q", "-B", "-Djacoco.skip=true", "-Dtest=AutomationModeTest", "-Dsurefire.failIfNoSpecifiedTests=true", "test"],
  { cwd: api, timeout: 1_200_000 });
check("testes do modo de automação na API passam", apiTests.status === 0, (apiTests.stdout + apiTests.stderr).slice(-400));
const webTests = run("npx", ["vitest", "run", "src/pages/ReviewQueuePage.test.tsx"], { cwd: web, timeout: 600_000 });
check("testes da fila de revisão passam", webTests.status === 0, webTests.stdout.slice(-400));
finish("AUTOMACAO_OK");
