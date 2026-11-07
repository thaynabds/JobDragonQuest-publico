// G14: Git Flow, Conventional Commits, PRs que fecham issues e tags SemVer anotadas.
import { problems } from "./commits.mjs";
import { check, finish, run } from "./lib.mjs";

const git = (...args) => run("git", args).stdout.trim();
run("git", ["fetch", "-q", "origin", "--tags"]);

check("branch develop publicada", git("ls-remote", "--heads", "origin", "develop") !== "");
const tags = git("tag", "--list", "v*", "--sort=-v:refname").split("\n").filter(Boolean);
const semver = tags.filter((tag) => /^v\d+\.\d+\.\d+$/.test(tag));
check("tags v0.1.0 e v0.2.0 existem", semver.includes("v0.1.0") && semver.includes("v0.2.0"), tags.join(" "));
const lightweight = tags.filter((tag) => git("cat-file", "-t", `refs/tags/${tag}`) !== "tag");
check(`toda tag v* é anotada e segue SemVer (${tags.length} tags)`, tags.length > 0 && lightweight.length === 0 && semver.length === tags.length,
  [...lightweight, ...tags.filter((tag) => !semver.includes(tag))].join(" "));
const latest = semver[0] ?? "";
check(`a tag mais recente (${latest}) aponta para a main publicada`,
  latest !== "" && git("rev-parse", `${latest}^{commit}`) === git("rev-parse", "origin/main"));

const mainCommits = git("rev-list", "--first-parent", "v0.1.0..origin/main").split("\n").filter(Boolean);
const direct = mainCommits.filter((sha) => git("rev-list", "--parents", "-n", "1", sha).split(" ").length < 3);
check(`main só recebeu merges depois da v0.1.0 (${mainCommits.length} commits)`, mainCommits.length > 0 && direct.length === 0, direct.join(" "));

// Mesma regra do CI (scripts/verificar/commits.mjs): Conventional Commits citando a issue, sem crédito a IA.
const work = git("log", "--no-merges", "--format=%B%x1e", "v0.1.0..origin/main").split("\x1e").map((m) => m.trim()).filter(Boolean);
const bad = work.filter((message) => problems(message).length > 0).map((message) => message.split("\n")[0]);
check(`commits seguem Conventional Commits e citam a issue (${work.length} commits)`, work.length > 0 && bad.length === 0, bad.join(" | "));

const prs = run("gh", ["pr", "list", "-R", "thaynabds/JobDragonQuest", "--state", "merged", "--limit", "1000", "--json", "number,baseRefName,headRefName,body"]);
const merged = prs.status === 0 ? JSON.parse(prs.stdout) : [];
const features = merged.filter((pr) => pr.baseRefName === "develop");
// PRs de trabalho saem de feature/issue-NN ou fix/issue-NN (dois antigos usaram docs/ e chore/) e fecham a issue. As voltas da main para a develop depois de
// release ou hotfix (chore/main-para-develop-X.Y.Z) e as atualizações do Dependabot não têm issue própria.
const backMerge = (pr) => /^chore\/main-para-develop-\d+\.\d+\.\d+$/.test(pr.headRefName) || /^dependabot\//.test(pr.headRefName);
const work_prs = features.filter((pr) => !backMerge(pr));
const offFlow = work_prs.filter((pr) => !(/^(feature|fix|docs|chore)\/issue-\d+-/.test(pr.headRefName) && /Closes #\d+/.test(pr.body)));
check(`PRs de trabalho com branch feature/issue-NN ou fix/issue-NN e 'Closes #N' (${work_prs.length} PRs)`,
  work_prs.length >= 15 && offFlow.length === 0, offFlow.map((pr) => `#${pr.number} ${pr.headRefName}`).join(", "));
check("release mesclada na main por PR", merged.some((pr) => pr.baseRefName === "main" && /^(develop|release\/\d+\.\d+\.\d+)$/.test(pr.headRefName)));
finish("GIT_FLOW_OK");
