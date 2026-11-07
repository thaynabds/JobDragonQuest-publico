#!/usr/bin/env node
// Cria um idioma novo para o painel a partir do português, traduzindo automaticamente.
//
// Uso:
//   node scripts/novo-idioma.mjs fr --nome "Français"
//   node scripts/novo-idioma.mjs it --nome "Italiano" --tradutor http://127.0.0.1:5000
//   node scripts/novo-idioma.mjs de --nome "Deutsch" --sem-traduzir      (copia o português para revisão humana)
//
// O tradutor é qualquer serviço compatível com a API do LibreTranslate (POST /translate). O padrão é um LibreTranslate
// local em http://127.0.0.1:5000, que roda sem enviar os textos para fora. Um endereço remoto só é aceito com HTTPS.
// Os marcadores {x} são protegidos antes de traduzir e conferidos depois: se a tradução estragar um marcador, a frase
// fica em português e aparece no relatório para revisão.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const I18N = join(ROOT, "src", "jobdragonquest-web", "src", "i18n");
const MAX_RESPONSE = 64 * 1024;
const TIMEOUT_MS = 20_000;

function fail(message) {
  console.error(`ERRO: ${message}`);
  process.exit(1);
}

export function parseArgs(argv) {
  const options = { code: null, name: null, translator: "http://127.0.0.1:5000", translate: true, out: null, register: true };
  const rest = [...argv];
  while (rest.length > 0) {
    const arg = rest.shift();
    if (arg === "--nome") options.name = rest.shift() ?? null;
    else if (arg === "--tradutor") options.translator = rest.shift() ?? null;
    else if (arg === "--sem-traduzir") options.translate = false;
    else if (arg === "--saida") options.out = rest.shift() ?? null;
    else if (arg === "--nao-registrar") options.register = false;
    else if (!arg.startsWith("--") && options.code === null) options.code = arg;
    else fail(`opção desconhecida: ${arg}`);
  }
  return options;
}

/** Código BCP 47 simples: "fr", "pt-PT", "zh-CN". Bloqueia caminhos e nomes estranhos no arquivo gerado. */
export function validCode(code) {
  return typeof code === "string" && /^[a-z]{2,3}(-[A-Z]{2})?$/.test(code);
}

/** Só tradutor local (loopback) por HTTP, ou remoto por HTTPS, sem usuário e senha na URL. */
export function validTranslator(raw) {
  let url;
  try {
    url = new URL(raw);
  } catch {
    return null;
  }
  if (url.username || url.password) return null;
  const loopback = ["127.0.0.1", "localhost", "[::1]"].includes(url.hostname);
  if (url.protocol === "https:" || (url.protocol === "http:" && loopback)) {
    return url.origin + url.pathname.replace(/\/+$/, "");
  }
  return null;
}

/** Lê as chaves e textos do português (arquivo TypeScript com strings JSON entre aspas duplas). */
export function readReference(source) {
  const entries = [];
  const pattern = /^\s*"([\w.-]+)":\s*("(?:[^"\\]|\\.)*")\s*,?\s*$/gm;
  const flat = source.replace(/:\s*\n\s*"/g, ': "');
  for (const match of flat.matchAll(pattern)) {
    entries.push([match[1], JSON.parse(match[2])]);
  }
  return entries;
}

const MARKER = /\{(\w+)\}/g;

export function protect(text) {
  const names = [];
  const masked = text.replace(MARKER, (_, name) => {
    names.push(name);
    return `[[${names.length - 1}]]`;
  });
  return { masked, names };
}

export function restore(translated, names) {
  let ok = true;
  const restored = translated.replace(/\[\[\s*(\d+)\s*\]\]/g, (_, index) => {
    const name = names[Number(index)];
    if (name === undefined) {
      ok = false;
      return "";
    }
    return `{${name}}`;
  });
  const expected = names.slice().sort().join(",");
  const got = [...restored.matchAll(MARKER)].map((m) => m[1]).sort().join(",");
  return ok && expected === got ? restored : null;
}

async function translate(base, text, target) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const response = await fetch(`${base}/translate`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ q: text, source: "pt", target: target.split("-")[0], format: "text" }),
      redirect: "error",
      signal: controller.signal,
    });
    const raw = await response.text();
    if (!response.ok || raw.length > MAX_RESPONSE) return null;
    const body = JSON.parse(raw);
    return typeof body.translatedText === "string" ? body.translatedText : null;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

function identifier(code) {
  return code.replace(/-([A-Za-z])/g, (_, c) => c.toUpperCase()).replace(/^[^a-z]/, "x");
}

function render(code, name, entries, translated) {
  const lines = entries.map(([key]) => `  ${JSON.stringify(key)}: ${JSON.stringify(translated.get(key))},`);
  return `// ${name} (${code}). Gerado por scripts/novo-idioma.mjs a partir do português; revise antes de publicar.
// Tipado contra pt-BR: o TypeScript recusa chave faltando ou sobrando.
import type { Messages } from "./pt-BR";

export const ${identifier(code)}: Messages = {
${lines.join("\n")}
};
`;
}

function register(code, name) {
  const file = join(I18N, "messages.ts");
  let source = readFileSync(file, "utf8");
  const id = identifier(code);
  if (source.includes(`./locales/${code}"`)) return;
  source = source.replace('import { ptBR } from "./locales/pt-BR";', `import { ${id} } from "./locales/${code}";\nimport { ptBR } from "./locales/pt-BR";`);
  source = source.replace(/export const LOCALES = \{([^}]*)\} as const;/, (_, inner) => `export const LOCALES = {${inner.trimEnd()}, ${JSON.stringify(code)}: ${id} } as const;`);
  source = source.replace(/export const LOCALE_NAMES: Record<Locale, string> = \{([^}]*)\};/, (_, inner) => `export const LOCALE_NAMES: Record<Locale, string> = {${inner.trimEnd()}, ${JSON.stringify(code)}: ${JSON.stringify(name)} };`);
  writeFileSync(file, source);
}

export async function main(argv) {
  const options = parseArgs(argv);
  if (!validCode(options.code)) fail("informe o código do idioma, ex.: fr, it, de, pt-PT");
  if (!options.name || options.name.length > 40 || /[<>"\\\n]/.test(options.name)) fail("informe --nome com até 40 caracteres, ex.: --nome \"Français\"");
  const outDir = options.out ? resolve(options.out) : join(I18N, "locales");
  const target = join(outDir, `${options.code}.ts`);
  if (existsSync(target)) fail(`o idioma ${options.code} já existe em ${target}`);
  const base = options.translate ? validTranslator(options.translator) : null;
  if (options.translate && !base) fail("o tradutor precisa ser local (http://127.0.0.1) ou HTTPS");

  const entries = readReference(readFileSync(join(I18N, "locales", "pt-BR.ts"), "utf8"));
  if (entries.length < 50) fail("não consegui ler os textos do português");
  const translated = new Map();
  const review = [];
  for (const [key, text] of entries) {
    if (!options.translate) {
      translated.set(key, text);
      review.push(key);
      continue;
    }
    const { masked, names } = protect(text);
    const result = await translate(base, masked, options.code);
    const restored = result === null ? null : restore(result, names);
    if (restored === null || restored.trim() === "") {
      translated.set(key, text);
      review.push(key);
    } else {
      translated.set(key, restored);
    }
  }
  mkdirSync(outDir, { recursive: true });
  writeFileSync(target, render(options.code, options.name, entries, translated));
  if (options.register && !options.out) register(options.code, options.name);
  console.log(`Idioma ${options.name} (${options.code}) criado em ${target}: ${entries.length - review.length} traduzidos, ${review.length} para revisar.`);
  if (review.length > 0) console.log(`Revise: ${review.slice(0, 20).join(", ")}${review.length > 20 ? "…" : ""}`);
  console.log("NOVO_IDIOMA_OK");
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main(process.argv.slice(2));
}
