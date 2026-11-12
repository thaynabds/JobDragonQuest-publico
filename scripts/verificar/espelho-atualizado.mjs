// G40: o espelho público está igual ao repositório principal (main, develop e tag da versão) e passa na auditoria
// de dados pessoais; o README publicado na main já mostra a versão nova.
import { check, finish, run } from "./lib.mjs";

const VERSION = "v0.8.4";
const remote = (name, ref) => (run("git", ["ls-remote", name, ref]).stdout.split("\t")[0] ?? "").trim();
run("git", ["fetch", "-q", "origin"]);
run("git", ["fetch", "-q", "publico", "main", "develop"]);
const tree = (ref) => run("git", ["rev-parse", `${ref}^{tree}`]).stdout.trim();
for (const branch of ["main", "develop"]) {
  const origin = tree(`origin/${branch}`);
  const mirror = tree(`publico/${branch}`);
  check(`espelho com o mesmo conteúdo do principal em ${branch}`, origin !== "" && origin === mirror, `${origin} x ${mirror}`);
}
check(`tag ${VERSION} no principal e no espelho`, remote("origin", `refs/tags/${VERSION}`) !== "" && remote("publico", `refs/tags/${VERSION}`) !== "");
const audit = run("bash", ["scripts/publicar-espelho.sh", "--verificar"], { timeout: 600_000 });
check("auditoria do espelho (e-mails privados, sem notas, sem vazamento)", audit.status === 0 && audit.stdout.includes("ESPELHO_VERIFICADO_OK"), (audit.stdout + audit.stderr).slice(-300));
const readme = run("gh", ["api", "repos/thaynabds/JobDragonQuest-publico/contents/README.md?ref=main", "-q", ".content"]);
const text = Buffer.from(readme.stdout.replace(/\s/g, ""), "base64").toString("utf8");
check("README público na versão nova", text.includes(VERSION.slice(1)) && text.includes("## Instalação rápida"));
// CI verde na main dos dois repositórios, na execução do commit publicado, e página de Release da versão.
for (const repo of ["thaynabds/JobDragonQuest", "thaynabds/JobDragonQuest-publico"]) {
  const runs = run("gh", ["run", "list", "-R", repo, "-b", "main", "-w", "ci.yml", "-L", "1", "--json", "headSha,status,conclusion"]);
  const forSha = runs.status === 0 ? JSON.parse(runs.stdout) : [];
  check(`${repo}: último CI da main concluído com sucesso`,
    forSha.length > 0 && forSha[0].status === "completed" && forSha[0].conclusion === "success", JSON.stringify(forSha[0] ?? runs.stderr));
  const release = run("gh", ["release", "view", VERSION, "-R", repo, "--json", "tagName,isDraft"]);
  check(`${repo}: página de Release ${VERSION} publicada`, release.status === 0 && /"isDraft":false/.test(release.stdout), release.stderr);
}
finish("ESPELHO_ATUALIZADO_OK");
