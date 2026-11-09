#!/usr/bin/env node
// Avisa no CI quando o AccessAbility Widget instalado não é o mais recente publicado no npm. Só avisa: uma versão nova
// não deve quebrar trabalho que não tem relação com ela.
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const PACOTE = "@thaynabds/accessability-widget";
const WEB = join(resolve(dirname(fileURLToPath(import.meta.url)), "..", ".."), "src", "jobdragonquest-web");
const instalada = JSON.parse(readFileSync(join(WEB, "package.json"), "utf8")).dependencies[PACOTE];
try {
  const ultima = execFileSync("npm", ["view", PACOTE, "version"], { encoding: "utf8", timeout: 30_000 }).trim();
  console.log(instalada === ultima ? `Widget em dia: ${instalada}` : `::warning::AccessAbility Widget ${instalada} instalado; a versão mais recente é ${ultima}.`);
} catch {
  console.log("::warning::Não foi possível consultar a versão do AccessAbility Widget.");
}
