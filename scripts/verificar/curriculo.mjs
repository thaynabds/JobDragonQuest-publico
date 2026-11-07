// G47: o painel gera currículo e carta a partir do perfil, geral ou ajustado à vaga, prontos para imprimir em PDF,
// sem inventar experiência. Testes na API, no painel e no MCP.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const api = join(ROOT, "src", "jobdragonquest-api");
const web = join(ROOT, "src", "jobdragonquest-web");
const service = readFileSync(join(api, "src/main/java/dev/jobdragonquest/career/CareerService.java"), "utf8");
check("currículo usa só o perfil (competências do perfil, com destaque)", service.includes("p.skills().stream()") && service.includes("highlighted"));
check("carta lista o que falta em vez de inventar", service.includes("missing.add("));
const page = readFileSync(join(web, "src/pages/ResumePage.tsx"), "utf8");
check("imprimir ou salvar em PDF pelo navegador", page.includes("window.print()"));
check("estilo de impressão esconde o painel", readFileSync(join(web, "src/styles.css"), "utf8").includes("@media print"));
check("MCP entrega currículo e carta", ["jobdragonquest_curriculo", "jobdragonquest_carta"].every((n) => readFileSync(join(ROOT, "src/jobdragonquest-mcp/src/tools.ts"), "utf8").includes(n)));
const apiTests = run("./mvnw", ["-q", "-B", "-Djacoco.skip=true", "-Dtest=CareerTest", "-Dsurefire.failIfNoSpecifiedTests=true", "test"], { cwd: api, timeout: 1_200_000 });
check("testes de currículo e carta na API passam", apiTests.status === 0, (apiTests.stdout + apiTests.stderr).slice(-400));
const report = readFileSync(join(api, "target/surefire-reports/TEST-dev.jobdragonquest.career.CareerTest.xml"), "utf8");
check("rodaram os testes de currículo, carta e isolamento", ["resumeAndLetterUseOnlyProfileData", "savedLetterTemplate", "otherAccountsCannotReadCareerData"].every((t) => report.includes(t)) && !/<failure|<error/.test(report));
const webTests = run("npx", ["vitest", "run", "src/pages/Career.test.tsx"], { cwd: web, timeout: 600_000 });
check("testes do currículo no painel passam", webTests.status === 0, webTests.stdout.slice(-400));
finish("CURRICULO_OK");
