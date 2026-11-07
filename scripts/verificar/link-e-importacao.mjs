// G37: cadastrar vaga a partir do link (lendo os dados da página com proteção contra SSRF) e importar o histórico de
// candidaturas por CSV sem duplicar. Testes na API, no painel e no MCP.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const api = join(ROOT, "src", "jobdragonquest-api");
const web = join(ROOT, "src", "jobdragonquest-web");
const mcp = join(ROOT, "src", "jobdragonquest-mcp");
const main = join(api, "src/main/java/dev/jobdragonquest");
const fetcher = readFileSync(join(main, "jobs/link/SafeWebFetcher.java"), "utf8");
check("não segue redirecionamento sozinho", fetcher.includes("HttpClient.Redirect.NEVER"));
check("checa cada salto do redirecionamento", /for \(int hop = 0; hop <= MAX_REDIRECTS/.test(fetcher) && fetcher.includes("checkDestination(current)"));
check("recusa endereços internos", ["isLoopbackAddress", "isSiteLocalAddress", "isLinkLocalAddress", "0xfc"].every((term) => fetcher.includes(term)));
check("limita tamanho da página", fetcher.includes("readNBytes(MAX_BYTES + 1)"));
const importer = readFileSync(join(main, "applications/ApplicationCsvImporter.java"), "utf8");
check("importação com chave por linha (não duplica)", importer.includes('"csv-" + digest(row)'));
check("importação limitada em linhas", /MAX_ROWS = 500/.test(importer));
for (const locale of ["pt-BR", "en", "es"]) {
  const text = readFileSync(join(web, "src/i18n/locales", `${locale}.ts`), "utf8");
  check(`textos de link e importação em ${locale}`, ["jobs.fromLink", "board.importCsv", "error.link_blocked"].every((key) => text.includes(`"${key}"`)));
}
check("o MCP lê vaga do link sem gravar", readFileSync(join(mcp, "src/tools.ts"), "utf8").includes("jobdragonquest_ler_vaga_do_link"));
const apiTests = run("./mvnw", ["-q", "-B", "-Djacoco.skip=true",
  "-Dtest=SafeWebFetcherTest,JobPageExtractorTest,JobLinkTest,JobLinkBlockedTest,ApplicationCsvImportTest", "-Dsurefire.failIfNoSpecifiedTests=true", "test"],
  { cwd: api, timeout: 1_200_000 });
check("testes de link e importação na API passam", apiTests.status === 0, (apiTests.stdout + apiTests.stderr).slice(-400));
const webTests = run("npx", ["vitest", "run", "src/pages/LinkAndImport.test.tsx"], { cwd: web, timeout: 600_000 });
check("testes de link e importação no painel passam", webTests.status === 0, webTests.stdout.slice(-400));
const mcpTests = run("npx", ["vitest", "run", "--reporter=verbose"], { cwd: mcp, timeout: 600_000 });
check("testes do MCP passam", mcpTests.status === 0 && mcpTests.stdout.includes("ler vaga do link"), mcpTests.stdout.slice(-400));
finish("LINK_IMPORTACAO_OK");
