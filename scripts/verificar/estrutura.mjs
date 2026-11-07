// G12: estrutura obrigatória do repositório, com o conteúdo exigido em cada documento.
import { existsSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT } from "./lib.mjs";

const read = (path) => (existsSync(join(ROOT, path)) ? readFileSync(join(ROOT, path), "utf8") : "");
const count = (text, pattern) => (text.match(pattern) ?? []).length;

const charter = read("docs/project-charter.md");
check("docs/project-charter.md com justificativa, visão, objetivos SMART, escopo e stakeholders",
  ["Justificativa", "Visão", "Objetivos SMART", "Dentro do escopo", "Fora do escopo", "Stakeholders", "Público-alvo"].every((s) => charter.includes(s)));

const requisitos = read("docs/requisitos.md");
const rf = requisitos.match(/\[RF\d{2}\]/g) ?? [];
const rnf = requisitos.match(/\[RNF\d{2}\]/g) ?? [];
check("requisitos funcionais numerados e únicos (pelo menos 15)", rf.length >= 15 && new Set(rf).size === rf.length, `${rf.length} RF`);
check("requisitos não funcionais numerados e únicos (pelo menos 8)", rnf.length >= 8 && new Set(rnf).size === rnf.length, `${rnf.length} RNF`);
check("RNFs cobrem desempenho, persistência, usabilidade, segurança e portabilidade",
  ["Desempenho", "Persistência", "Usabilidade", "Segurança", "Portabilidade"].every((s) => requisitos.includes(s)));

const casos = read("docs/casos-de-uso.md");
check("casos de uso com atores", casos.includes("## Atores"));
check("pelo menos 5 casos de uso com fluxo principal, alternativo e de exceção",
  count(casos, /^## UC\d{2}/gm) >= 5 && count(casos, /Fluxo principal/g) >= 5 && count(casos, /Fluxos? alternativos?/g) >= 5 && count(casos, /Fluxos? de exceção/g) >= 5,
  `${count(casos, /^## UC\d{2}/gm)} casos`);

const stories = read("docs/user-stories.md");
const canonical = count(stories, /\*\*Como\*\* .+, \*\*quero\*\* .+ \*\*para que\*\* .+/g);
check("pelo menos 12 histórias no padrão Como/quero/para que", canonical >= 12, `${canonical} histórias`);
check("critérios de aceite em Gherkin para cada história",
  count(stories, /^\s*Dado /gm) >= canonical && count(stories, /^\s*Quando /gm) >= canonical && count(stories, /^\s*Então /gm) >= canonical);

const diagramas = read("docs/diagramas.md");
check("diagramas C4, de classes e DER em Mermaid",
  count(diagramas, /```mermaid/g) >= 3 && diagramas.includes("erDiagram") && diagramas.includes("classDiagram") && diagramas.includes("C4"));

for (const dir of ["src/jobdragonquest-api", "src/jobdragonquest-web"]) {
  check(`${dir} existe`, existsSync(join(ROOT, dir)) && statSync(join(ROOT, dir)).isDirectory());
}
const gitignore = read(".gitignore");
check(".gitignore ignora dependências, builds e segredos", ["node_modules/", "target/", "dist/", ".env"].every((s) => gitignore.includes(s)));

const readme = read("README.md");
check("README com pelo menos 3 badges", count(readme, /img\.shields\.io|badge\.svg/g) >= 3);
check("README separa implementado de planejado", readme.includes("Implementado") && readme.includes("Planejado"));
const docs = ["project-charter.md", "requisitos.md", "casos-de-uso.md", "user-stories.md", "diagramas.md"];
check("README tem link para cada documento obrigatório", docs.every((doc) => readme.includes(`](docs/${doc})`)));
check("README tem a tabela do fluxo de branches", readme.includes("| `main`") && readme.includes("| `develop`") && readme.includes("feature/issue-"));
check("README traz instruções de execução", readme.includes("scripts/configurar-local.sh") && readme.includes("scripts/iniciar.sh"));
finish("ESTRUTURA_OK");
