// G34: perfil completo. Competências com anos, meses e dias; idiomas por nome; senioridade de aprendiz a executivo;
// campos profissionais e de formulário; testes da API e do painel passando.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const api = join(ROOT, "src", "jobdragonquest-api");
const web = join(ROOT, "src", "jobdragonquest-web");
const expected = ["APPRENTICE", "INTERN", "TRAINEE", "ENTRY", "JUNIOR", "MID", "SENIOR", "SPECIALIST", "LEAD", "COORDINATOR", "MANAGER", "DIRECTOR", "EXECUTIVE"];

const javaEnum = readFileSync(join(api, "src/main/java/dev/jobdragonquest/common/domain/Seniority.java"), "utf8");
const javaValues = [...javaEnum.matchAll(/^\s{4}([A-Z_]+)\(\d\)[,;]/gm)].map((m) => m[1]);
check(`API com as 13 senioridades em ordem (${javaValues.length})`, JSON.stringify(javaValues) === JSON.stringify(expected), javaValues.join(" "));
const types = readFileSync(join(web, "src/api/types.ts"), "utf8");
check("painel com as mesmas 13 senioridades", expected.every((value) => types.includes(`"${value}"`)));
for (const locale of ["pt-BR", "en", "es"]) {
  const messages = readFileSync(join(web, "src/i18n/locales", `${locale}.ts`), "utf8");
  check(`textos das senioridades em ${locale}`, expected.every((value) => messages.includes(`"seniority.${value}"`)));
}
const migration = readFileSync(join(api, "src/main/resources/db/migration/V6__perfil_completo.sql"), "utf8");
check("migração com tempo em meses e dias", /exp_months/.test(migration) && /exp_days/.test(migration));

const apiTests = run("./mvnw", ["-q", "-B", "-Dtest=Profile*Test,MatchingEngineTest", "-Dsurefire.failIfNoSpecifiedTests=true", "test"], { cwd: api, timeout: 1_200_000 });
check("testes de perfil e compatibilidade da API passam", apiTests.status === 0, (apiTests.stdout + apiTests.stderr).slice(-400));
const webTests = run("npx", ["vitest", "run", "src/pages/ProfileEditorPage.test.tsx", "src/i18n"], { cwd: web, timeout: 600_000 });
check("testes da tela de perfil e das traduções passam", webTests.status === 0, webTests.stdout.slice(-400));
finish("PERFIL_COMPLETO_OK");
