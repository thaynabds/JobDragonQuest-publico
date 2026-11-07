// G38: "Analisar perguntas e respostas para esta vaga" e "Revisar candidatura antes de enviar" funcionam com os dados
// reais do perfil, sem inventar nada. Testes na API, no painel e no MCP.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const api = join(ROOT, "src", "jobdragonquest-api");
const web = join(ROOT, "src", "jobdragonquest-web");
const mcp = join(ROOT, "src", "jobdragonquest-mcp");
const assistant = readFileSync(join(api, "src/main/java/dev/jobdragonquest/assist/ApplicationAssistant.java"), "utf8");
check("dado ausente vira pendência, não resposta", assistant.includes("Complete no perfil") && assistant.includes("Só adicione se você realmente tiver"));
check("a revisão bloqueia vaga encerrada e impedimentos", assistant.includes("job_closed") && assistant.includes("blocker_"));
check("a revisão avisa candidatura repetida", assistant.includes("already_applied"));
const card = readFileSync(join(web, "src/pages/AssistCard.tsx"), "utf8");
check("os dois botões estão na vaga", card.includes("assist.analyze") && card.includes("assist.review"));
for (const locale of ["pt-BR", "en", "es"]) {
  const text = readFileSync(join(web, "src/i18n/locales", `${locale}.ts`), "utf8");
  check(`textos do assistente em ${locale}`, ["assist.analyze", "assist.review", "checkStatus.BLOCK"].every((key) => text.includes(`"${key}"`)));
}
const pt = readFileSync(join(web, "src/i18n/locales/pt-BR.ts"), "utf8");
check("rótulos pedidos pela dona", pt.includes('"Analisar perguntas e respostas para esta vaga"') && pt.includes('"Revisar candidatura antes de enviar"'));
const tools = readFileSync(join(mcp, "src/tools.ts"), "utf8");
check("o MCP tem respostas e revisão", tools.includes("jobdragonquest_respostas_para_vaga") && tools.includes("jobdragonquest_revisar_candidatura"));
const apiTests = run("./mvnw", ["-q", "-B", "-Djacoco.skip=true", "-Dtest=AssistTest", "-Dsurefire.failIfNoSpecifiedTests=true", "test"],
  { cwd: api, timeout: 1_200_000 });
check("testes do assistente na API passam", apiTests.status === 0, (apiTests.stdout + apiTests.stderr).slice(-400));
const webTests = run("npx", ["vitest", "run", "src/pages/AssistCard.test.tsx"], { cwd: web, timeout: 600_000 });
check("testes do assistente no painel passam", webTests.status === 0, webTests.stdout.slice(-400));
const mcpTests = run("npx", ["vitest", "run", "--reporter=verbose"], { cwd: mcp, timeout: 600_000 });
check("testes do MCP passam", mcpTests.status === 0 && mcpTests.stdout.includes("respostas e revisão"), mcpTests.stdout.slice(-400));
finish("PERGUNTAS_REVISAO_OK");
