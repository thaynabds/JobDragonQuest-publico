// G24: auditoria de vazamento. Procura, nos arquivos versionados e nos logs locais, segredos, tokens, dados pessoais
// fora do permitido, metadados sensíveis em imagens e PDF, e código que registre token ou senha.
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

// Padrões montados em partes para este arquivo não casar consigo mesmo.
const PERSONAL_EMAIL = new RegExp(["thaynabds", "tec@", "gmail\\.com"].join(""), "i");
const SECRETS = [
  ["token pessoal do JobFly", new RegExp("jfp" + "_[A-Za-z0-9_-]{43}(?![A-Za-z0-9_-])")],
  ["JWT", new RegExp("ey" + "J[A-Za-z0-9_-]{10,}\\.ey" + "J[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}")],
  ["chave privada", new RegExp("-----BEGIN [A-Z ]*PRIVATE" + " KEY")],
  ["token do GitHub", new RegExp("gh" + "[pousr]_[A-Za-z0-9]{30,}")],
  ["chave da Anthropic ou OpenAI", new RegExp("sk-" + "(ant-)?[A-Za-z0-9_-]{20,}")],
  ["token do Discord", new RegExp("[MN][A-Za-z\\d]{23}\\.[\\w-]{6}\\.[\\w-]{27}")],
  ["chave da AWS", new RegExp("AKIA" + "[0-9A-Z]{16}")],
];
// Exceções conferidas à mão: só estas linhas, com o motivo. Qualquer outro achado reprova.
const ALLOWED = [
  { file: "src/jobfly-api/src/test/java/dev/jobfly/identity/AuthIntegrationTest.java", marker: ".Zm9yamFkbw\"", reason: "JWT forjado de propósito (assinatura \"forjado\") para testar que a API recusa token falso" },
];
const allowed = (file, line) => ALLOWED.some((entry) => entry.file === file && line.includes(entry.marker));
const CPF = /\b\d{3}\.\d{3}\.\d{3}-\d{2}\b/;
const PHONE_BR = /(\+55\s?)?\(?\b\d{2}\)?\s?9\d{4}-?\d{4}\b/;

const files = run("git", ["ls-files"]).stdout.split("\n").filter(Boolean);
check(`arquivos versionados listados (${files.length})`, files.length > 100);

const forbidden = files.filter((file) => /(^|\/)\.env$|^data\/|\.pem$|\.key$|id_rsa|id_ed25519|(^|\/)congo\.env$|mcp-token$/.test(file));
check("nenhum .env, dado local, chave ou token versionado", forbidden.length === 0, forbidden.join(" "));

const textFiles = files.filter((file) => !/\.(png|jpg|jpeg|gif|ico|pdf|woff2?|jar|class)$/i.test(file) && existsSync(join(ROOT, file)));
const hits = { secret: [], email: [], cpf: [], phone: [] };
for (const file of textFiles) {
  if (statSync(join(ROOT, file)).size > 2_000_000) continue;
  const text = readFileSync(join(ROOT, file), "utf8");
  for (const [name, pattern] of SECRETS) {
    const lines = text.split("\n").filter((line) => pattern.test(line) && !allowed(file, line));
    if (lines.length > 0) hits.secret.push(`${file} (${name})`);
  }
  if (PERSONAL_EMAIL.test(text)) hits.email.push(file);
  if (CPF.test(text)) hits.cpf.push(file);
  if (PHONE_BR.test(text) && !file.endsWith("package-lock.json")) hits.phone.push(file);
}
check(`nenhum segredo nos ${textFiles.length} arquivos de texto`, hits.secret.length === 0, hits.secret.join(" "));
check("e-mail pessoal da dona não aparece nos arquivos", hits.email.length === 0, hits.email.join(" "));
check("nenhum CPF nos arquivos", hits.cpf.length === 0, hits.cpf.join(" "));
check("nenhum telefone nos arquivos", hits.phone.length === 0, hits.phone.join(" "));

// Metadados: PNG sem blocos de texto ou EXIF; PDF sem e-mail.
const pngs = files.filter((file) => file.toLowerCase().endsWith(".png"));
const withMetadata = pngs.filter((file) => {
  const data = readFileSync(join(ROOT, file));
  return ["tEXt", "iTXt", "zTXt", "eXIf"].some((chunk) => data.includes(Buffer.from(chunk, "latin1")));
});
check(`${pngs.length} imagens PNG sem metadados (texto ou EXIF)`, pngs.length > 0 && withMetadata.length === 0, withMetadata.join(" "));
const pdfInfo = run("pdfinfo", ["docs/resumo-jobfly.pdf"]);
check("PDF sem e-mail nos metadados", pdfInfo.status === 0 && !PERSONAL_EMAIL.test(pdfInfo.stdout) && !/@/.test(pdfInfo.stdout), pdfInfo.stdout);

// Código: nada de registrar token ou senha, e o painel não guarda token no navegador.
const source = files.filter((file) => /^(src|integrations)\/.*\.(java|ts|tsx|py)$/.test(file) && !/test/i.test(file));
const logLines = [];
for (const file of source) {
  readFileSync(join(ROOT, file), "utf8").split("\n").forEach((line, index) => {
    if (/(log\.(info|warn|debug|error|trace)|console\.(log|warn|error|info)|print\(|logging\.)/.test(line) &&
        /(token|password|senha|secret|authorization)\b(?!\s*(inválido|ausente|não|do JobFly|em formato))/i.test(line.replace(/"[^"]*"/g, '""'))) {
      logLines.push(`${file}:${index + 1}`);
    }
  });
}
check(`nenhum log de token, senha ou segredo em ${source.length} arquivos de código`, logLines.length === 0, logLines.join(" "));
const storage = source.filter((file) => file.startsWith("src/jobfly-web/")).flatMap((file) =>
  readFileSync(join(ROOT, file), "utf8").split("\n").filter((line) => /(local|session)Storage\.setItem/.test(line) && /token/i.test(line)).map(() => file));
check("o painel não guarda token no localStorage ou sessionStorage", storage.length === 0, storage.join(" "));

// Logs locais da execução (data/logs): nada de token, JWT ou senha.
const logsDir = join(ROOT, "data", "logs");
const logFiles = existsSync(logsDir) ? readdirSync(logsDir).filter((name) => name.endsWith(".log")) : [];
const leakedLogs = logFiles.filter((name) => {
  const text = readFileSync(join(logsDir, name), "utf8");
  return SECRETS.slice(0, 2).some(([, pattern]) => pattern.test(text)) || /password=|senha=|JOBFLY_JWT_SECRET=/i.test(text);
});
check(`logs locais sem token, JWT ou senha (${logFiles.length} arquivos)`, leakedLogs.length === 0, leakedLogs.join(" "));

// Permissões dos segredos locais (fora do Git).
const envFile = join(ROOT, ".env");
check(".env local com permissão 600", !existsSync(envFile) || (statSync(envFile).mode & 0o777) === 0o600);
const gitignore = readFileSync(join(ROOT, ".gitignore"), "utf8");
check(".gitignore cobre .env, data/ e GATES.md", [".env", "data/", "GATES.md"].every((entry) => gitignore.includes(entry)));
finish("SEM_VAZAMENTO_OK");
