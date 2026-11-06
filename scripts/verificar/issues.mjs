// G13: pelo menos 15 issues no GitHub, todas com label e critérios de aceite na descrição.
import { check, finish, run } from "./lib.mjs";

const result = run("gh", ["issue", "list", "-R", "thaynabds/JobFly", "--state", "all", "--limit", "200", "--json", "number,labels,body"]);
check("consulta às issues do GitHub", result.status === 0, result.stderr);
const issues = result.status === 0 ? JSON.parse(result.stdout) : [];
const valid = issues.filter((issue) => issue.labels.length > 0 && /Critérios de aceite/.test(issue.body) && /\*\*Dado\*\*/.test(issue.body));
check(`pelo menos 15 issues com label e critérios de aceite (${valid.length} de ${issues.length})`, valid.length >= 15);
finish("ISSUES_OK");
