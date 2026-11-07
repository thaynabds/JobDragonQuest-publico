// G42: os filtros do perfil valem na coleta e na compatibilidade (empresas excluídas, palavras incluídas e excluídas,
// tipos de contrato, autorização de trabalho e salário mínimo), com testes passando.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const api = join(ROOT, "src", "jobdragonquest-api");
const main = join(api, "src/main/java/dev/jobdragonquest");
const filters = readFileSync(join(main, "matching/PreferenceFilters.java"), "utf8");
for (const code of ["EXCLUDED_COMPANY", "EXCLUDED_KEYWORD", "MISSING_KEYWORD", "EMPLOYMENT_TYPE", "WORK_AUTHORIZATION", "SALARY"]) {
  check(`impedimento ${code} existe`, filters.includes(`"${code}"`));
}
const engine = readFileSync(join(main, "matching/MatchingEngine.java"), "utf8");
check("a compatibilidade aplica os filtros", /PreferenceFilters\.check\(profile, job, blockers, warnings\)/.test(engine));
const collector = readFileSync(join(main, "sources/SourceCollector.java"), "utf8");
check("a coleta tira as vagas fora das preferências", /PreferenceFilters\.hardBlockers\(/.test(collector));
const snapshot = readFileSync(join(main, "profiles/ProfileSnapshot.java"), "utf8");
check("o retrato do perfil leva os filtros salvos", /details\.excludedCompanies\(\)/.test(snapshot) && /details\.workAuthorization\(\)/.test(snapshot));

const tests = run("./mvnw", ["-q", "-B", "-Djacoco.skip=true", "-Dtest=PreferenceFiltersTest,SourceCollectorTest,MatchingEngineTest",
  "-Dsurefire.failIfNoSpecifiedTests=true", "test"], { cwd: api, timeout: 1_200_000 });
check("testes de filtros, coleta e compatibilidade passam", tests.status === 0, (tests.stdout + tests.stderr).slice(-400));
const report = readFileSync(join(api, "target/surefire-reports/TEST-dev.jobdragonquest.sources.SourceCollectorTest.xml"), "utf8");
check("o teste de integração dos filtros rodou", report.includes("profileFiltersApplyToCollectionAndMatching")
  && !/<failure|<error/.test(report));
finish("FILTROS_OK");
