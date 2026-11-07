// G22: os três READMEs trazem o prompt pronto para IA (link, instalação, MCP, pergunta sobre deploy opcional) e
// tudo o que o prompt cita existe de verdade (scripts, guia de deploy e ferramentas do MCP).
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT } from "./lib.mjs";

const tools = new Set([...readFileSync(join(ROOT, "src", "jobfly-mcp", "src", "tools.ts"), "utf8").matchAll(/name: "(jobfly_[a-z_]+)"/g)].map((m) => m[1]));
check(`ferramentas do MCP lidas (${tools.size})`, tools.size >= 17);

const languages = {
  "README.md": { question: /só rodar no seu computador.*internet/s, never: /Nunca me peça a senha nem o token/, paid: /Nunca use serviço pago/ },
  "README.en.md": { question: /only on your computer.*internet/s, never: /Never ask me for the password or the token/, paid: /Never use a paid service/ },
  "README.es.md": { question: /solo en tu computadora.*internet/s, never: /Nunca me pidas la contraseña ni el token/, paid: /Nunca uses un servicio pago/ },
};

for (const [file, rules] of Object.entries(languages)) {
  const readme = readFileSync(join(ROOT, file), "utf8");
  const block = /```text\n([\s\S]*?)```/.exec(readme)?.[1] ?? "";
  check(`${file}: tem o bloco de prompt`, block.length > 500);
  check(`${file}: prompt traz o link do repositório`, block.includes("https://github.com/thaynabds/JobFly"));
  check(`${file}: prompt manda clonar e instalar`, block.includes("git clone https://github.com/thaynabds/JobFly.git") && block.includes("scripts/instalar.sh"));
  check(`${file}: prompt liga o MCP`, block.includes("scripts/conectar-mcp.mjs") && block.includes('"jobfly"'));
  check(`${file}: prompt pergunta sobre deploy opcional`, rules.question.test(block) && block.includes("docs/deploy.md"));
  check(`${file}: prompt protege senha e token`, rules.never.test(block));
  check(`${file}: prompt recusa serviço pago`, rules.paid.test(block));
  const cited = [...block.matchAll(/\b(jobfly_[a-z_]+)\b/g)].map((m) => m[1]);
  const unknown = cited.filter((name) => !tools.has(name));
  check(`${file}: ferramentas citadas existem (${cited.length})`, cited.length >= 3 && unknown.length === 0, unknown.join(" "));
  const paths = [...block.matchAll(/\b((?:scripts|docs)\/[\w./-]+?)(?=[\s,;:)]|$)/g)].map((m) => m[1].replace(/\.$/, ""));
  const missing = paths.filter((path) => !existsSync(join(ROOT, path)));
  check(`${file}: arquivos citados existem (${paths.length})`, paths.length >= 4 && missing.length === 0, missing.join(" "));
  check(`${file}: link para o guia de deploy na documentação`, readme.includes("](docs/deploy.md)"));
}

const deploy = existsSync(join(ROOT, "docs", "deploy.md")) ? readFileSync(join(ROOT, "docs", "deploy.md"), "utf8") : "";
check("docs/deploy.md existe", deploy.length > 1000);
check("deploy só com opções gratuitas", /gratuit/i.test(deploy) && /pedir pagamento/.test(deploy));
check("deploy exige a lista de e-mails permitidos", deploy.includes("JOBFLY_ALLOWED_EMAILS"));
check("deploy diz o que não foi testado", deploy.includes("O que não foi testado aqui"));
check("Dockerfile da API existe e roda sem root", /USER jobfly/.test(readFileSync(join(ROOT, "src", "jobfly-api", "Dockerfile"), "utf8")));
finish("README_PROMPT_OK");
