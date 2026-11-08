// G6: docs/historico-issues-prs.md traz o histórico real de issues e PRs do repositório principal, sem campos pessoais,
// e docs/governanca.md explica o espelho. Itens criados depois da exportação (PR da release e volta para a develop)
// podem faltar; todo o resto tem de estar lá com o mesmo título, e o que já estava fechado tem de constar fechado.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { cell, closedIssues, loadHistory } from "../exportar-historico.mjs";
import { check, finish, ROOT } from "./lib.mjs";

check("closedIssues lê Closes/Fixes/Resolves", JSON.stringify(closedIssues("Closes #9. fixes #31 e Resolves #2; ref #7")) === "[2,9,31]");
check("cell neutraliza tabela e HTML", cell("a|b\n<script>") === "a\\|b &lt;script&gt;");

const text = readFileSync(join(ROOT, "docs", "historico-issues-prs.md"), "utf8");
const section = (title) => text.split(`## ${title}`)[1]?.split("\n## ")[0] ?? "";
const rows = (body) => new Map(body.split("\n").filter((line) => /^\| \d+ \|/.test(line))
  .map((line) => { const parts = line.split(" | "); return [Number(parts[0].slice(2)), { title: parts[1], state: parts[2] }]; }));
const fileIssues = rows(section("Issues"));
const filePulls = rows(section("Pull requests"));

const live = loadHistory();
const maxFile = Math.max(0, ...fileIssues.keys(), ...filePulls.keys());
// PRs do Dependabot aparecem sozinhos toda semana; a exportação é regerada a cada release, então eles não contam para
// a exigência de exportação recente (os que já estavam no arquivo continuam conferidos abaixo).
const human = (item) => !/^app\/dependabot$|^dependabot/.test(item.author?.login ?? "");
const maxLive = Math.max(0, ...live.issues.map((i) => i.number), ...live.pulls.filter(human).map((p) => p.number));
check(`exportação recente (maior número no arquivo ${maxFile}, no GitHub ${maxLive})`, maxFile > 100 && maxLive - maxFile <= 3);

const problems = [];
for (const [kind, items, fileRows] of [["issue", live.issues, fileIssues], ["PR", live.pulls, filePulls]]) {
  for (const item of items.filter((entry) => entry.number <= maxFile)) {
    const row = fileRows.get(item.number);
    if (!row) problems.push(`${kind} ${item.number} ausente`);
    else if (row.title !== cell(item.title)) problems.push(`${kind} ${item.number} com outro título`);
    else if ((item.state === "CLOSED" || item.state === "MERGED") && row.state !== item.state) problems.push(`${kind} ${item.number} com estado ${row.state}`);
  }
}
check(`todas as issues (${live.issues.length}) e PRs (${live.pulls.length}) até o ${maxFile} conferem com o GitHub`, problems.length === 0, problems.slice(0, 8).join("; "));
check(`tabela de PRs tem as ${filePulls.size} linhas exportadas (mínimo 60)`, filePulls.size >= 60);
const personal = /[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+\.[A-Za-z.]{2,}|\bauthor\b|autor:/i;
check("nenhum campo pessoal exportado (e-mail, autor)", !personal.test(text) && personal.test("x pessoa@exemplo.com"));
const governance = readFileSync(join(ROOT, "docs", "governanca.md"), "utf8");
check("governanca.md explica o espelho e aponta o histórico", /espelho/.test(governance) && governance.includes("historico-issues-prs.md"));
finish("HISTORICO_OK");
