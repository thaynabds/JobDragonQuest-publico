// G31: o projeto se chama JobDragonQuest em tudo o que é versionado (exceto as migrações já aplicadas, que o Flyway
// confere pela soma) e o slogan aparece no painel, nos READMEs e no resumo em PDF.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const OLD = new RegExp(["job", "fly"].join(""), "i");
const files = run("git", ["ls-files"]).stdout.split("\n").filter(Boolean);
const textFiles = files.filter((file) => !/\.(png|pdf|woff2?|jar)$/i.test(file) && !/db\/migration\/V[1-5]__/.test(file));
const leftovers = textFiles.filter((file) => OLD.test(file) || OLD.test(readFileSync(join(ROOT, file), "utf8")));
check(`nome antigo fora de ${textFiles.length} arquivos e caminhos`, leftovers.length === 0, leftovers.slice(0, 10).join(" "));
check("pastas renomeadas", ["src/jobdragonquest-api/pom.xml", "src/jobdragonquest-web/package.json", "src/jobdragonquest-mcp/package.json"].every((f) => files.includes(f)));
const slogans = {
  "README.md": "Queime as etapas da sua procura por vagas.",
  "README.en.md": "Burn through the stages of your job search.",
  "README.es.md": "Quema etapas en tu búsqueda de empleo.",
};
for (const [file, slogan] of Object.entries(slogans)) {
  const text = readFileSync(join(ROOT, file), "utf8");
  check(`${file} com o nome e o slogan`, text.includes("# JobDragonQuest") && text.includes(slogan));
}
const locales = ["pt-BR", "en", "es"].map((l) => readFileSync(join(ROOT, "src/jobdragonquest-web/src/i18n/locales", `${l}.ts`), "utf8"));
check("painel com o nome e o slogan nos 3 idiomas", locales.every((t) => t.includes('"app.name": "JobDragonQuest"')) &&
  ["Queime as etapas", "Burn through the stages", "Quema etapas"].every((s, i) => locales[i].includes(s)));
const pdf = run("pdftotext", ["docs/resumo-jobdragonquest.pdf", "-"]).stdout;
check("resumo em PDF com o nome novo e o slogan", pdf.includes("JobDragonQuest") && pdf.includes("Queime as etapas") && !OLD.test(pdf));
const remote = run("git", ["remote", "get-url", "origin"]).stdout;
check("remoto aponta para o repositório JobDragonQuest", /thaynabds\/JobDragonQuest(\.git)?\s*$/.test(remote));
finish("NOME_OK");
