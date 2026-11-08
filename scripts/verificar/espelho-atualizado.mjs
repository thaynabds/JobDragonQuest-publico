// G40: o espelho público está igual ao repositório principal (main, develop e tag da versão) e passa na auditoria
// de dados pessoais; o README publicado na main já mostra a versão nova.
import { check, finish, run } from "./lib.mjs";

const VERSION = "v0.4.2";
const remote = (name, ref) => (run("git", ["ls-remote", name, ref]).stdout.split("\t")[0] ?? "").trim();
for (const ref of ["refs/heads/main", "refs/heads/develop", `refs/tags/${VERSION}`]) {
  const origin = remote("origin", ref);
  const mirror = remote("publico", ref);
  check(`espelho igual ao principal em ${ref}`, origin !== "" && origin === mirror, `${origin} x ${mirror}`);
}
const audit = run("bash", ["scripts/publicar-espelho.sh", "--verificar"], { timeout: 600_000 });
check("auditoria do espelho (e-mails privados, sem notas, sem vazamento)", audit.status === 0 && audit.stdout.includes("ESPELHO_VERIFICADO_OK"), (audit.stdout + audit.stderr).slice(-300));
const readme = run("gh", ["api", "repos/thaynabds/JobDragonQuest-publico/contents/README.md?ref=main", "-q", ".content"]);
const text = Buffer.from(readme.stdout.replace(/\s/g, ""), "base64").toString("utf8");
check("README público na versão nova", text.includes("0.4.2") && text.includes("## Instalação rápida"));
// CI verde na main dos dois repositórios, na execução do commit publicado, e página de Release da versão.
const mainSha = remote("origin", "refs/heads/main");
for (const repo of ["thaynabds/JobDragonQuest", "thaynabds/JobDragonQuest-publico"]) {
  const runs = run("gh", ["run", "list", "-R", repo, "-b", "main", "-w", "ci.yml", "-L", "20", "--json", "headSha,status,conclusion"]);
  const forSha = runs.status === 0 ? JSON.parse(runs.stdout).filter((entry) => entry.headSha === mainSha) : [];
  check(`${repo}: CI da main em ${mainSha.slice(0, 7)} concluído com sucesso`,
    forSha.length > 0 && forSha[0].status === "completed" && forSha[0].conclusion === "success", JSON.stringify(forSha[0] ?? runs.stderr));
  const release = run("gh", ["release", "view", VERSION, "-R", repo, "--json", "tagName,isDraft"]);
  check(`${repo}: página de Release ${VERSION} publicada`, release.status === 0 && /"isDraft":false/.test(release.stdout), release.stderr);
}
finish("ESPELHO_ATUALIZADO_OK");
