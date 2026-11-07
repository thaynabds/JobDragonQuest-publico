// G48: ferramentas de carreira sem custo: preparação de entrevista pelos requisitos da vaga, faixa salarial das vagas
// coletadas e competências a desenvolver, no painel e no MCP.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const api = join(ROOT, "src", "jobdragonquest-api");
const web = join(ROOT, "src", "jobdragonquest-web");
const mcp = join(ROOT, "src", "jobdragonquest-mcp");
const controller = readFileSync(join(api, "src/main/java/dev/jobdragonquest/career/CareerController.java"), "utf8");
check("rotas de entrevista, salário e competências", ["/interview", "/api/v1/career/salary", "/api/v1/career/skills"].every((r) => controller.includes(r)));
const service = readFileSync(join(api, "src/main/java/dev/jobdragonquest/career/CareerService.java"), "utf8");
check("estatísticas só das vagas da conta", service.includes("jobs.recentSnapshots(userId, MARKET_POOL)"));
check("painel tem Carreira e Preparar entrevista", readFileSync(join(web, "src/components/Layout.tsx"), "utf8").includes('"/carreira"')
  && readFileSync(join(web, "src/pages/AssistCard.tsx"), "utf8").includes("api.interview"));
for (const locale of ["pt-BR", "en", "es"]) {
  const text = readFileSync(join(web, "src/i18n/locales", `${locale}.ts`), "utf8");
  check(`textos de carreira em ${locale}`, ["career.title", "career.negotiation", "assist.interview", "resume.print"].every((k) => text.includes(`"${k}"`)));
}
const tools = readFileSync(join(mcp, "src/tools.ts"), "utf8");
check("MCP tem entrevista e mercado", tools.includes("jobdragonquest_preparar_entrevista") && tools.includes("jobdragonquest_mercado"));
const apiTests = run("./mvnw", ["-q", "-B", "-Djacoco.skip=true", "-Dtest=CareerTest", "-Dsurefire.failIfNoSpecifiedTests=true", "test"], { cwd: api, timeout: 1_200_000 });
check("testes de carreira na API passam", apiTests.status === 0, (apiTests.stdout + apiTests.stderr).slice(-400));
const webTests = run("npx", ["vitest", "run", "src/pages/Career.test.tsx", "src/pages/AssistCard.test.tsx"], { cwd: web, timeout: 600_000 });
check("testes de carreira no painel passam", webTests.status === 0, webTests.stdout.slice(-400));
const mcpTests = run("npx", ["vitest", "run", "--reporter=verbose"], { cwd: mcp, timeout: 600_000 });
check("testes do MCP passam", mcpTests.status === 0 && mcpTests.stdout.includes("currículo e carreira"), mcpTests.stdout.slice(-400));
finish("CARREIRA_OK");
