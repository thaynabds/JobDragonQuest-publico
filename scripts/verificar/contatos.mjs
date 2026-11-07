// G45: cada candidatura guarda contatos de recrutadores e gestores, só para a conta dona, e registrar uma conversa
// leva a candidatura recém-enviada para a etapa de contato. Testes na API, no painel e no MCP.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const api = join(ROOT, "src", "jobdragonquest-api");
const web = join(ROOT, "src", "jobdragonquest-web");
const mcp = join(ROOT, "src", "jobdragonquest-mcp");
const migration = readFileSync(join(api, "src/main/resources/db/migration/V7__contatos_candidatura.sql"), "utf8");
check("tabela de contatos apaga junto com a candidatura", /references job_application \(id\) on delete cascade/.test(migration));
const service = readFileSync(join(api, "src/main/java/dev/jobdragonquest/applications/ApplicationContactService.java"), "utf8");
check("contato só da conta dona", service.includes("findByIdAndUserId(applicationId, userId)"));
check("conversa registrada leva para contato com recrutador", service.includes("ApplicationState.RECRUITER_CONTACT"));
check("auditoria guarda só o id, sem e-mail ou nome", /audit\.record\([^;]*contact\.getId\(\)\.toString\(\)\)/.test(service) && !/audit\.record\([^;]*getEmail/.test(service));
const tools = readFileSync(join(mcp, "src/tools.ts"), "utf8");
check("o MCP registra contato sem mudar etapa", tools.includes("jobdragonquest_registrar_contato") && /contacted: false/.test(tools));
for (const locale of ["pt-BR", "en", "es"]) {
  const text = readFileSync(join(web, "src/i18n/locales", `${locale}.ts`), "utf8");
  check(`textos dos contatos em ${locale}`, ["contacts.title", "contactRole.RECRUITER", "error.contact_limit"].every((key) => text.includes(`"${key}"`)));
}
const apiTests = run("./mvnw", ["-q", "-B", "-Djacoco.skip=true", "-Dtest=ApplicationContactTest", "-Dsurefire.failIfNoSpecifiedTests=true", "test"],
  { cwd: api, timeout: 1_200_000 });
check("testes dos contatos na API passam", apiTests.status === 0, (apiTests.stdout + apiTests.stderr).slice(-400));
const webTests = run("npx", ["vitest", "run", "src/pages/ContactsSection.test.tsx"], { cwd: web, timeout: 600_000 });
check("testes dos contatos no painel passam", webTests.status === 0, webTests.stdout.slice(-400));
const mcpTests = run("npx", ["vitest", "run", "--reporter=verbose"], { cwd: mcp, timeout: 600_000 });
check("testes do MCP passam", mcpTests.status === 0 && mcpTests.stdout.includes("registrar contato"), mcpTests.stdout.slice(-400));
finish("CONTATOS_OK");
