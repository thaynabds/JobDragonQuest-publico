// G27: português, inglês e espanhol completos, detecção do idioma e criação automática de idiomas novos.
import { spawn } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { readReference } from "../novo-idioma.mjs";
import { check, finish, ROOT, run } from "./lib.mjs";

const web = join(ROOT, "src", "jobdragonquest-web");
const i18n = join(web, "src", "i18n");
check("arquivo do espanhol existe", existsSync(join(i18n, "locales", "es.ts")));
const hub = readFileSync(join(i18n, "messages.ts"), "utf8");
check("pt-BR, en e es registrados", /"pt-BR": ptBR, en, es/.test(hub));
const tests = run("npx", ["vitest", "run", "src/i18n"], { cwd: web, timeout: 300_000 });
check("testes de tradução passam (chaves, marcadores, detecção)", tests.status === 0, tests.stdout.slice(-400));

const reference = readReference(readFileSync(join(i18n, "locales", "pt-BR.ts"), "utf8"));
check(`português lido pelo script (${reference.length} textos)`, reference.length > 250);

// Tradutor falso, compatível com LibreTranslate: traduz tudo e estraga de propósito o marcador [[1]].
const server = createServer((req, res) => {
  let body = "";
  req.on("data", (chunk) => (body += chunk));
  req.on("end", () => {
    const { q } = JSON.parse(body);
    const translated = q.includes("[[1]]") ? `«${q.replace("[[1]]", "")}»` : `«${q}»`;
    res.setHeader("Content-Type", "application/json");
    res.end(JSON.stringify({ translatedText: translated }));
  });
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const port = server.address().port;

function script(args) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [join(ROOT, "scripts", "novo-idioma.mjs"), ...args], { cwd: ROOT });
    let out = "";
    child.stdout.on("data", (d) => (out += d));
    child.stderr.on("data", (d) => (out += d));
    child.on("close", (status) => resolve({ status, out }));
  });
}

const dir = mkdtempSync(join(tmpdir(), "jobdragonquest-idioma-"));
try {
  const created = await script(["fr", "--nome", "Français", "--tradutor", `http://127.0.0.1:${port}`, "--saida", dir, "--nao-registrar"]);
  check("script cria o idioma novo com o tradutor local", created.status === 0 && created.out.includes("NOVO_IDIOMA_OK"), created.out);
  const generated = existsSync(join(dir, "fr.ts")) ? readReference(readFileSync(join(dir, "fr.ts"), "utf8")) : [];
  check("idioma novo tem todas as chaves do português", generated.length === reference.length, `${generated.length}/${reference.length}`);
  const byKey = new Map(generated);
  const markers = (text) => [...(text ?? "").matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join(",");
  const broken = reference.filter(([key, text]) => markers(byKey.get(key)) !== markers(text));
  check("todos os marcadores {x} preservados", broken.length === 0, broken.map(([k]) => k).join(" "));
  const translatedCount = reference.filter(([key, text]) => byKey.get(key) === `«${text}»`).length;
  check(`textos traduzidos pelo serviço (${translatedCount})`, translatedCount > 200);
  const fallback = reference.find(([, text]) => markers(text).split(",").length >= 2);
  check("tradução que estraga marcador volta para o português", !fallback || byKey.get(fallback[0]) === fallback[1]);

  const remote = await script(["it", "--nome", "Italiano", "--tradutor", "http://example.com", "--saida", dir, "--nao-registrar"]);
  check("recusa tradutor remoto sem HTTPS", remote.status === 1);
  const traversal = await script(["../../x", "--nome", "X", "--sem-traduzir", "--saida", dir]);
  check("recusa código de idioma inválido", traversal.status === 1);
  const again = await script(["fr", "--nome", "Français", "--sem-traduzir", "--saida", dir, "--nao-registrar"]);
  check("não sobrescreve idioma existente", again.status === 1);
} finally {
  server.close();
  rmSync(dir, { recursive: true, force: true });
}
finish("IDIOMAS_OK");
