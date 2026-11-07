// G4: governança do repositório conferida por máquina: licença, changelog com todas as tags, guia de contribuição,
// política de segurança, templates, Dependabot, índice de docs, versão única, rastreabilidade RF → issue → teste e CI
// com relatório de testes, análise de dependências, checagem de commits e lint.
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { basename, join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const read = (file) => (existsSync(join(ROOT, file)) ? readFileSync(join(ROOT, file), "utf8") : "");

// Licença coerente com o README.
const license = read("LICENSE");
check("LICENSE existe e diz 'Todos os direitos reservados'", /Todos os direitos reservados/.test(license) && /All rights reserved/.test(license));
check("README cita a mesma licença", /Todos os direitos reservados/.test(read("README.md")) && /\[LICENSE\]\(LICENSE\)|\(LICENSE\)/.test(read("README.md")));

// Changelog com todas as tags.
const tags = run("git", ["tag", "--list", "v*", "--sort=-v:refname"]).stdout.split("\n").filter(Boolean);
const changelog = read("CHANGELOG.md");
const missingTags = tags.filter((tag) => !changelog.includes(`## [${tag.slice(1)}] - `) || !changelog.includes(`[${tag.slice(1)}]: https://`));
check(`CHANGELOG tem seção e link para as ${tags.length} tags`, tags.length > 0 && missingTags.length === 0, missingTags.join(" "));
check("CHANGELOG segue o Keep a Changelog", /keepachangelog\.com/.test(changelog) && /### (Adicionado|Corrigido|Alterado)/.test(changelog));

// Contribuição, segurança e templates.
const contributing = read("CONTRIBUTING.md");
check("CONTRIBUTING explica Git Flow, commits, testes e segurança",
  ["feature/issue-NN", "Conventional Commits", "./mvnw verify", "npm run verify", "Flyway", "IDOR"].every((term) => contributing.includes(term)));
const security = read("SECURITY.md");
check("SECURITY.md diz como relatar em particular", /Report a vulnerability/.test(security) && /Não abra issue pública/.test(security));
for (const template of ["bug.yml", "feature.yml", "docs.yml", "config.yml"]) {
  check(`template de issue ${template}`, read(`.github/ISSUE_TEMPLATE/${template}`).length > 50);
}
check("template de issue pede critério de aceite", /Dado \/ Quando \/ Então/.test(read(".github/ISSUE_TEMPLATE/bug.yml")));
const pr = read(".github/pull_request_template.md");
check("template de PR tem Closes, testes, docs e segurança", ["Closes #", "./mvnw verify", "CHANGELOG.md", "IDOR"].every((term) => pr.includes(term)));

// Dependabot cobre todos os ecossistemas com manifesto.
const dependabot = read(".github/dependabot.yml");
for (const [ecosystem, directory] of [["maven", "/src/jobdragonquest-api"], ["npm", "/src/jobdragonquest-web"], ["npm", "/src/jobdragonquest-mcp"], ["github-actions", "/"]]) {
  const block = new RegExp(`package-ecosystem: ${ecosystem}\\s+directory: ${directory.replace(/\//g, "\\/")}\\s`);
  check(`Dependabot: ${ecosystem} em ${directory}`, block.test(dependabot));
}

// Índice de docs completo.
const index = read("docs/README.md");
const documents = readdirSync(join(ROOT, "docs")).filter((file) => /\.(md|pdf)$/.test(file) && file !== "README.md");
const unindexed = documents.filter((file) => !index.includes(`](${file})`));
check(`docs/README.md indexa os ${documents.length} documentos`, documents.length > 10 && unindexed.length === 0, unindexed.join(", "));
check("docs/README.md tem a regra de sincronização", /Regra de sincronização documentação × código/.test(index));
check("docs/governanca.md explica o espelho", /espelho/.test(read("docs/governanca.md")) && /historico-issues-prs\.md/.test(read("docs/governanca.md")));

// Versão única: tag mais recente = manifestos = badges dos três READMEs.
const latest = (tags[0] ?? "").slice(1);
const pomVersion = (read("src/jobdragonquest-api/pom.xml").match(/<artifactId>jobdragonquest-api<\/artifactId>\s*<version>([^<]+)<\/version>/) ?? [])[1];
const versions = {
  "pom.xml": pomVersion,
  "web/package.json": JSON.parse(read("src/jobdragonquest-web/package.json") || "{}").version,
  "mcp/package.json": JSON.parse(read("src/jobdragonquest-mcp/package.json") || "{}").version,
  "extensão/manifest.json": JSON.parse(read("extensao-navegador/manifest.json") || "{}").version,
};
for (const readme of ["README.md", "README.en.md", "README.es.md"]) {
  versions[readme] = (read(readme).match(/img\.shields\.io\/badge\/[^-)]+-(\d+\.\d+\.\d+)-/) ?? [])[1];
}
const divergent = Object.entries(versions).filter(([, version]) => version !== latest);
check(`versão única ${latest} na tag, nos manifestos e nos badges`, latest !== "" && divergent.length === 0,
  divergent.map(([file, version]) => `${file}=${version}`).join(" "));

// Rastreabilidade RF → issue → teste.
const files = run("git", ["ls-files"]).stdout.split("\n").filter(Boolean);
const names = new Set(files.map((file) => basename(file).replace(/\.java$/, "")));
const paths = new Set(files);
const rows = read("docs/requisitos.md").split("\n").filter((line) => /^\| \[RF\d\d\] \|/.test(line));
check("requisitos.md tem os RF com coluna de issue", rows.length >= 28 && rows.every((row) => row.split("|").length === 7), `${rows.length} linhas`);
const weak = [];
for (const row of rows) {
  const [, id, , status, issue, trace] = row.split("|").map((part) => part.trim());
  if (status !== "✅") continue;
  const cited = [...trace.matchAll(/`([^`]+)`/g)].map((match) => match[1]);
  const tests = cited.filter((name) => (/(Test|\.test\.(tsx?|mjs)|test_[\w]+\.py)$/.test(name) || /^scripts\//.test(name))
    && (names.has(name) || paths.has(name) || names.has(basename(name))));
  if (!/#\d+|v0\.1\.0/.test(issue) || tests.length === 0) weak.push(id);
}
check("todo RF entregue cita issue e um teste que existe", weak.length === 0, weak.join(" "));

// CI: relatório de testes, análise de dependências, checagem de commits e lint.
const ci = read(".github/workflows/ci.yml");
check("CI publica os relatórios de teste como artefato", /actions\/upload-artifact@/.test(ci) && /surefire-reports/.test(ci));
check("CI escreve o total de testes no resumo da execução", /GITHUB_STEP_SUMMARY/.test(ci) && /resumo-testes\.mjs/.test(ci));
check("CI analisa dependências (npm audit e revisão de dependências nos PRs)", /npm audit/.test(ci) && /actions\/dependency-review-action@/.test(ci));
check("CI confere Conventional Commits nos PRs", /scripts\/verificar\/commits\.mjs/.test(ci));
check("painel roda lint no verify", /"lint":\s*"eslint/.test(read("src/jobdragonquest-web/package.json"))
  && /npm run lint/.test(JSON.parse(read("src/jobdragonquest-web/package.json") || "{}").scripts?.verify ?? ""));
check("API documenta o formato padrão de erro", /## Formato de erro/.test(read("docs/api.md")) && /RFC 9457/.test(read("docs/api.md")));

finish("GOVERNANCA_OK");
