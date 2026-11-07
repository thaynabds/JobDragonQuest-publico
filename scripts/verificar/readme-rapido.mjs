// G50: os três READMEs mostram logo no começo os requisitos, os comandos de instalar e rodar prontos para copiar e
// como conferir que funcionou. Os comandos são testados de verdade: clona o espelho público como o README manda
// e roda a checagem de pré-requisitos do instalador.
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const titles = { "README.md": "Instalação rápida", "README.en.md": "Quick install", "README.es.md": "Instalación rápida" };
let commands = null;
for (const [file, title] of Object.entries(titles)) {
  const text = readFileSync(join(ROOT, file), "utf8");
  const sections = text.split(/\n(?=## )/);
  const first = sections.find((part) => part.startsWith("## "));
  check(`${file}: a primeira seção é "${title}"`, first?.startsWith(`## ${title}\n`), first?.split("\n")[0]);
  const section = first ?? "";
  check(`${file}: lista os requisitos`, /Java 21/.test(section) && /Node 20/.test(section) && /PostgreSQL/.test(section));
  const block = section.match(/```bash\n([\s\S]*?)```/)?.[1] ?? "";
  const lines = block.trim().split("\n");
  const clone = lines[0]?.match(/^git clone (\S+) (\S+)$/);
  check(`${file}: clona numa pasta com nome definido`, Boolean(clone), lines[0]);
  check(`${file}: entra na mesma pasta que clonou`, clone && lines[1] === `cd ${clone[2]}`, lines[1]);
  check(`${file}: instala com um comando`, lines[2] === "scripts/instalar.sh", lines[2]);
  check(`${file}: diz como conferir que funcionou`, section.includes("INSTALACAO_CONCLUIDA") && section.includes("http://127.0.0.1:5173"));
  check(`${file}: nenhum cd solto seguido de outro cd`, !/^cd src\/.*\n^cd src\//m.test(section));
  if (file === "README.md" && clone) commands = { url: clone[1], dir: clone[2] };
}

const installer = readFileSync(join(ROOT, "scripts/instalar.sh"), "utf8");
check("o instalador termina com INSTALACAO_CONCLUIDA", installer.includes('echo "INSTALACAO_CONCLUIDA"'));
const vite = readFileSync(join(ROOT, "src/jobdragonquest-web/vite.config.ts"), "utf8");
check("o painel sobe na porta que o README diz", /5173/.test(vite) || !/port:/.test(vite));

if (commands) {
  const work = mkdtempSync(join(tmpdir(), "jdq-readme-"));
  try {
    const cloned = run("git", ["clone", "--depth", "1", "-q", commands.url, commands.dir], { cwd: work, timeout: 300_000 });
    check("o git clone do README funciona", cloned.status === 0, cloned.stderr.slice(-300));
    const pre = run("bash", ["scripts/instalar.sh", "--verificar"], { cwd: join(work, commands.dir), timeout: 300_000 });
    check("a checagem do instalador roda na pasta clonada", pre.status === 0 && pre.stdout.includes("PRE_REQUISITOS_OK"),
      (pre.stdout + pre.stderr).slice(-300));
  } finally {
    rmSync(work, { recursive: true, force: true });
  }
}
finish("README_RAPIDO_OK");
