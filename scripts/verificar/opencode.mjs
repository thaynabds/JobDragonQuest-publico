// G9: o OpenCode está instalado e a configuração do projeto é válida e restritiva.
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const version = run("opencode", ["--version"]);
check("opencode instalado", version.status === 0 && /^\d+\.\d+\.\d+/.test(version.stdout.trim()), version.stderr || version.error);

let config = null;
try {
  config = JSON.parse(readFileSync(join(ROOT, "opencode.json"), "utf8"));
} catch (error) {
  check("opencode.json é JSON válido", false, error);
}
check("opencode.json declara o schema oficial", config?.$schema === "https://opencode.ai/config.json");
check("OpenCode não pode editar arquivos", config?.permission?.edit === "deny");
check("OpenCode não pode rodar comandos", config?.permission?.bash === "deny");
check("OpenCode não pode buscar na web", config?.permission?.webfetch === "deny");
check("OpenCode não lê .env, data/ nem a auditoria pessoal",
  config?.permission?.read?.[".env"] === "deny" && config?.permission?.read?.["data/**"] === "deny"
    && config?.permission?.read?.["privado/**"] === "deny");
check("instruções do projeto carregadas", Array.isArray(config?.instructions) && config.instructions.includes("AGENTS.md"));

const agents = existsSync(join(ROOT, "AGENTS.md")) ? readFileSync(join(ROOT, "AGENTS.md"), "utf8") : "";
check("AGENTS.md existe com as regras do projeto", agents.includes("crédito") && agents.includes(".env") && agents.includes("src/jobdragonquest-api"));

const resolved = run("opencode", ["debug", "config"], { timeout: 90_000 });
check("OpenCode aceita a configuração do projeto", resolved.status === 0 && resolved.stdout.includes('"edit": "deny"'), resolved.stderr.slice(0, 300));
finish("OPENCODE_OK");
