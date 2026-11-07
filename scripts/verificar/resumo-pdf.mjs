// G15: resumo em PDF com 1 a 2 parágrafos (ideia e stack) e a URL do repositório.
import { existsSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const pdf = join(ROOT, "docs", "resumo-jobdragonquest.pdf");
check("docs/resumo-jobdragonquest.pdf existe", existsSync(pdf));
const text = run("pdftotext", ["-layout", pdf, "-"]);
check("PDF legível como texto", text.status === 0, text.stderr || text.error);
const content = text.stdout;
check("traz a URL do repositório", content.includes("github.com/thaynabds/JobDragonQuest-publico"));
check("descreve a stack", ["Java 21", "Spring Boot", "React", "TypeScript", "PostgreSQL"].every((s) => content.includes(s)));
const plain = run("pdftotext", [pdf, "-"]).stdout;
const paragraphs = plain.split(/\n\s*\n/).map((p) => p.replace(/\s+/g, " ").trim()).filter((p) => p.split(" ").length >= 25);
check(`tem de 1 a 2 parágrafos de texto (${paragraphs.length})`, paragraphs.length >= 1 && paragraphs.length <= 2);
finish("RESUMO_PDF_OK");
