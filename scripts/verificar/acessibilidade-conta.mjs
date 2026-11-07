// G35: as preferências de acessibilidade ficam salvas na conta e voltam depois do login em outro navegador, com
// validação estrita na API e no painel.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const api = join(ROOT, "src", "jobdragonquest-api");
const web = join(ROOT, "src", "jobdragonquest-web");
const service = readFileSync(join(api, "src/main/java/dev/jobdragonquest/settings/AccessibilitySettings.java"), "utf8");
check("API aceita só chaves e valores conhecidos", service.includes("THEMES") && service.includes("FILTERS") && service.includes("FONT_SCALES") && service.includes("TOGGLES.contains(name)"));
const prefs = readFileSync(join(web, "src/a11y/prefs.ts"), "utf8");
const javaToggles = (service.match(/TOGGLES = Set\.of\(([^;]+)\)/s)?.[1] ?? "").match(/"([a-zA-Z]+)"/g)?.map((t) => t.replaceAll('"', "")) ?? [];
check(`painel e API com os mesmos recursos (${javaToggles.length})`, javaToggles.length === 13 && javaToggles.every((t) => prefs.includes(`"${t}"`)));
const sync = readFileSync(join(web, "src/a11y/A11yAccountSync.tsx"), "utf8");
check("o painel passa o que vem da conta pelo sanitize", sync.includes("update(sanitize(data))"));
check("sincronização ligada no app", readFileSync(join(web, "src/App.tsx"), "utf8").includes("<A11yAccountSync />"));
const apiTests = run("./mvnw", ["-q", "-B", "-Djacoco.skip=true", "-Dtest=AccessibilitySettingsTest", "-Dsurefire.failIfNoSpecifiedTests=true", "test"],
  { cwd: api, timeout: 1_200_000 });
check("testes da API passam", apiTests.status === 0, (apiTests.stdout + apiTests.stderr).slice(-400));
const webTests = run("npx", ["vitest", "run", "src/a11y"], { cwd: web, timeout: 600_000 });
check("testes de acessibilidade no painel passam", webTests.status === 0, webTests.stdout.slice(-400));
finish("ACESSIBILIDADE_CONTA_OK");
