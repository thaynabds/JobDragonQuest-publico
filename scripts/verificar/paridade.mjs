// G51: a matriz de paridade privada não tem recurso da referência sem equivalente (⬜). Só 🔑 é aceito, e cada um
// precisa explicar a autorização externa que falta. A matriz fica em privado/ (fora do Git).
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const file = join(ROOT, "privado/auditoria/matriz-funcionalidades.md");
check("a matriz existe", existsSync(file));
check("a matriz está fora do Git", run("git", ["check-ignore", "-q", "privado/auditoria/matriz-funcionalidades.md"]).status === 0);
const rows = existsSync(file) ? readFileSync(file, "utf8").split("\n").filter((line) => /^\| (?!Recurso|---)/.test(line)) : [];
check(`a matriz lista os recursos (${rows.length})`, rows.length >= 20);
const pending = rows.filter((row) => row.trim().endsWith("⬜ |"));
check("nenhum recurso pendente", pending.length === 0, pending.map((row) => row.split("|")[1].trim()).join("; "));
const external = rows.filter((row) => row.includes("🔑"));
check("cada autorização externa está explicada", external.every((row) => /🔑 \S.{20,}/.test(row)));
finish("PARIDADE_OK");
