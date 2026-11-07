// G14: Git Flow, Conventional Commits, PRs que fecham issues e tags SemVer anotadas.
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

const conventional = /^(feat|fix|docs|refactor|test|chore|ci|build|perf|style)(\([a-z0-9-]+\))?!?: .+ \(#\d+\)$/;
const work = git("log", "--no-merges", "--format=%s", "v0.1.0..origin/main").split("\n").filter(Boolean);
const bad = work.filter((subject) => !conventional.test(subject));
check(`commits seguem Conventional Commits e citam a issue (${work.length} commits)`, work.length > 0 && bad.length === 0, bad.join(" | "));

const prs = run("gh", ["pr", "list", "-R", "thaynabds/JobDragonQuest", "--state", "merged", "--limit", "100", "--json", "number,baseRefName,headRefName,body"]);
const merged = prs.status === 0 ? JSON.parse(prs.stdout) : [];
const features = merged.filter((pr) => pr.baseRefName === "develop");
check("PRs de feature com branch feature/issue-NN e 'Closes #N'",
  features.length >= 15 && features.every((pr) => /^(feature|fix)\/issue-\d{2}-/.test(pr.headRefName) && /Closes #\d+/.test(pr.body)),
  `${features.length} PRs`);
check("release mesclado na main por PR", merged.some((pr) => pr.baseRefName === "main" && pr.headRefName === "develop"));
finish("GIT_FLOW_OK");
