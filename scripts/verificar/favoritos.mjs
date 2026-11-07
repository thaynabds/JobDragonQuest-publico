// G44: favoritar vagas e buscar vagas parecidas com as favoritas ou com as últimas candidaturas, com nota de
// semelhança ajustável e explicação, só com vagas da própria conta. Testes na API, no painel e no MCP.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const api = join(ROOT, "src", "jobdragonquest-api");
const web = join(ROOT, "src", "jobdragonquest-web");
const mcp = join(ROOT, "src", "jobdragonquest-mcp");
const main = join(api, "src/main/java/dev/jobdragonquest");
check("migração da coluna favorite", /add column favorite boolean not null default false/.test(
  readFileSync(join(api, "src/main/resources/db/migration/V8__vagas_favoritas.sql"), "utf8")));
const controller = readFileSync(join(main, "discovery/SimilarJobsController.java"), "utf8");
check("as duas bases existem (favoritas e candidaturas)", controller.includes("FAVORITES") && controller.includes("APPLIED"));
check("semelhança mínima limitada de 0 a 100", /@Min\(0\) @Max\(100\) int minScore/.test(controller));
const service = readFileSync(join(main, "jobs/JobService.java"), "utf8");
check("a busca usa só vagas da conta", service.includes("findByUserIdAndIdIn(userId, basisIds)") && service.includes("findByUserIdOrderByFoundAtDesc(userId, PageRequest.of(0, SIMILAR_POOL))"));
const similarity = readFileSync(join(main, "jobs/JobSimilarity.java"), "utf8");
check("a nota explica palavras e competências em comum", similarity.includes("sharedWords") || similarity.includes("intersection"));
const tools = readFileSync(join(mcp, "src/tools.ts"), "utf8");
check("o MCP tem vagas parecidas e favoritar", tools.includes("jobdragonquest_vagas_parecidas") && tools.includes("jobdragonquest_favoritar_vaga"));
for (const locale of ["pt-BR", "en", "es"]) {
  const text = readFileSync(join(web, "src/i18n/locales", `${locale}.ts`), "utf8");
  check(`textos de favoritas e parecidas em ${locale}`, ["similar.title", "similar.minScore", "jobs.favoriteThis"].every((key) => text.includes(`"${key}"`)));
}
const apiTests = run("./mvnw", ["-q", "-B", "-Djacoco.skip=true", "-Dtest=SimilarJobsTest", "-Dsurefire.failIfNoSpecifiedTests=true", "test"],
  { cwd: api, timeout: 1_200_000 });
check("testes de vagas parecidas na API passam", apiTests.status === 0, (apiTests.stdout + apiTests.stderr).slice(-400));
const webTests = run("npx", ["vitest", "run", "src/pages/JobsPage.test.tsx"], { cwd: web, timeout: 600_000 });
check("testes de favoritas no painel passam", webTests.status === 0, webTests.stdout.slice(-400));
const mcpTests = run("npx", ["vitest", "run", "--reporter=verbose"], { cwd: mcp, timeout: 600_000 });
check("testes do MCP passam", mcpTests.status === 0 && mcpTests.stdout.includes("vagas parecidas"), mcpTests.stdout.slice(-400));
finish("FAVORITOS_OK");
