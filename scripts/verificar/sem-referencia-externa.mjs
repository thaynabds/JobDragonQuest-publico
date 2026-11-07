// G36: nenhum arquivo versionado (conteúdo ou caminho) cita a ferramenta usada como referência na auditoria.
// O nome é montado em partes para este arquivo não casar consigo mesmo.
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const NAME = new RegExp(["job", "\\s*", "co", "pilot"].join(""), "i");
const files = run("git", ["ls-files"]).stdout.split("\n").filter(Boolean);
check(`arquivos versionados listados (${files.length})`, files.length > 100);
const byPath = files.filter((file) => NAME.test(file));
check("nenhum caminho cita a ferramenta", byPath.length === 0, byPath.join(" "));
const byContent = files.filter((file) => {
  if (/\.(png|jpg|pdf|woff2?|jar)$/i.test(file) || !existsSync(join(ROOT, file))) return false;
  return NAME.test(readFileSync(join(ROOT, file), "utf8"));
});
check("nenhum conteúdo cita a ferramenta", byContent.length === 0, byContent.join(" "));
const pdf = run("pdftotext", ["docs/resumo-jobdragonquest.pdf", "-"]);
check("resumo em PDF sem a ferramenta", pdf.status === 0 && !NAME.test(pdf.stdout));
const ignored = run("git", ["check-ignore", "-q", "privado/qualquer-arquivo.md"]);
check("pasta privado/ fica fora do Git", ignored.status === 0);
finish("SEM_REFERENCIA_OK");
