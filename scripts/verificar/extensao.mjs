// G49: extensão gratuita do navegador (instalada sem loja) que salva a vaga da página aberta no JobDragonQuest local
// com token pessoal. Permissões mínimas, sem código remoto, só fala com a API local; CORS da API sem cookies.
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const dir = join(ROOT, "extensao-navegador");
const manifest = JSON.parse(readFileSync(join(dir, "manifest.json"), "utf8"));
check("manifesto versão 3", manifest.manifest_version === 3);
check("permissões mínimas (activeTab e storage)", JSON.stringify([...manifest.permissions].sort()) === JSON.stringify(["activeTab", "storage"]));
check("só fala com a API local", JSON.stringify(manifest.host_permissions) === JSON.stringify(["http://127.0.0.1/*", "http://localhost/*"]));
check("política de conteúdo sem script remoto", /script-src 'self'/.test(manifest.content_security_policy?.extension_pages ?? ""));
const sources = readdirSync(dir).filter((f) => /\.(js|html)$/.test(f)).map((f) => readFileSync(join(dir, f), "utf8")).join("\n");
check("nenhum script de fora", !/<script[^>]+src=["']https?:/i.test(sources));
check("nada de innerHTML nem eval", !/innerHTML|eval\(|new Function/.test(sources));
check("token só no armazenamento local, sem sincronizar", sources.includes("chrome.storage.local") && !sources.includes("storage.sync"));
check("chamadas sem cookies", sources.includes('credentials: "omit"'));
const tests = run("node", ["--test"], { cwd: dir, timeout: 120_000 });
check("testes da extensão passam", tests.status === 0 && /# pass 4/.test(tests.stdout), tests.stdout.slice(-300));
const security = readFileSync(join(ROOT, "src/jobdragonquest-api/src/main/java/dev/jobdragonquest/common/security/SecurityConfig.java"), "utf8");
check("CORS de extensão sem cookies", /config\.setAllowCredentials\(false\)/.test(security) && security.includes("EXTENSION_PATHS"));
const corsTests = run("./mvnw", ["-q", "-B", "-Djacoco.skip=true", "-Dtest=ExtensionCorsTest", "-Dsurefire.failIfNoSpecifiedTests=true", "test"],
  { cwd: join(ROOT, "src/jobdragonquest-api"), timeout: 1_200_000 });
check("testes do CORS da extensão passam", corsTests.status === 0, (corsTests.stdout + corsTests.stderr).slice(-300));
const readme = readFileSync(join(ROOT, "README.md"), "utf8");
check("README explica a instalação da extensão", readme.includes("extensao-navegador"));
finish("EXTENSAO_OK");
