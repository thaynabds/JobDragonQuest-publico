// Utilitários dos verificadores de gates. Cada verificador falha com código 1 e só imprime o marcador de sucesso
// depois que todas as asserções passaram.
import { spawnSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const failures = [];

export function check(name, condition, detail = "") {
  if (condition) {
    console.log(`ok      ${name}`);
  } else {
    failures.push(name);
    console.log(`FALHOU  ${name}${detail ? `  ${String(detail).slice(0, 300)}` : ""}`);
  }
}

export function run(command, args, options = {}) {
  const result = spawnSync(command, args, { cwd: ROOT, encoding: "utf8", timeout: 120_000, ...options });
  return { status: result.status, stdout: result.stdout ?? "", stderr: result.stderr ?? "", error: result.error };
}

/** Encerra: código 0 com o marcador se nada falhou, código 1 caso contrário. */
export function finish(marker) {
  if (failures.length > 0) {
    console.log(`\n${failures.length} verificação(ões) falharam.`);
    process.exit(1);
  }
  console.log(marker);
}
