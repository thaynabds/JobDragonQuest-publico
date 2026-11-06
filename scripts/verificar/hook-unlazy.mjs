// G8: o hook de parada do unlazy está instalado neste projeto e aponta para um script que existe.
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT } from "./lib.mjs";

const file = join(ROOT, ".claude", "settings.local.json");
check("arquivo .claude/settings.local.json existe", existsSync(file));
let commands = [];
try {
  const settings = JSON.parse(readFileSync(file, "utf8"));
  commands = (settings.hooks?.Stop ?? []).flatMap((entry) => entry.hooks ?? []).map((hook) => hook.command ?? "");
} catch (error) {
  check("configuração do Claude Code é JSON válido", false, error);
}
const unlazy = commands.find((command) => command.includes("stop-hook.mjs") && command.includes("--unlazy-hook"));
check("hook Stop do unlazy registrado", Boolean(unlazy), commands.join(" | "));
const script = unlazy?.match(/'([^']*stop-hook\.mjs)'/)?.[1];
check("script do hook existe", Boolean(script) && existsSync(script), script);
finish("HOOK_UNLAZY_OK");
