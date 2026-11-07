// G23: a logo nova está no painel (barra lateral, login e ícones da aba), nos três READMEs e no resumo em PDF,
// sem metadados nas imagens.
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const PNG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const images = ["docs/assets/logo.png", "src/jobfly-web/public/logo.png", "src/jobfly-web/public/logo-192.png", "src/jobfly-web/public/favicon-32.png", "src/jobfly-web/public/favicon-64.png"];
for (const image of images) {
  const path = join(ROOT, image);
  const data = existsSync(path) ? readFileSync(path) : Buffer.alloc(0);
  check(`${image} é PNG válido`, data.subarray(0, 8).equals(PNG));
  check(`${image} sem metadados`, data.length > 0 && !["tEXt", "iTXt", "zTXt", "eXIf"].some((chunk) => data.includes(Buffer.from(chunk, "latin1"))));
  const width = data.length > 24 ? data.readUInt32BE(16) : 0;
  const height = data.length > 24 ? data.readUInt32BE(20) : 0;
  check(`${image} quadrada (${width}x${height})`, width > 0 && width === height);
}
check("docs/assets/logo.png com 512 px", readFileSync(join(ROOT, images[0])).readUInt32BE(16) === 512);

const web = (file) => readFileSync(join(ROOT, "src", "jobfly-web", file), "utf8");
check("ícone da aba usa a logo", /href="\/favicon-32\.png"/.test(web("index.html")) && /apple-touch-icon" href="\/logo-192\.png"/.test(web("index.html")));
check("barra lateral usa a logo", web("src/components/Layout.tsx").includes('src="/logo.png"'));
check("tela de login usa a logo", web("src/pages/LoginPage.tsx").includes('src="/logo.png"'));
check("paleta do painel em roxo", web("src/styles.css").includes("--primary: #7c3aed"));

for (const readme of ["README.md", "README.en.md", "README.es.md"]) {
  const text = readFileSync(join(ROOT, readme), "utf8");
  check(`${readme} abre com a logo e texto alternativo`, /^<p align="center">\s*<img src="docs\/assets\/logo\.png" alt="[^"]{20,}"/.test(text));
}

const pdf = run("pdfimages", ["-list", "docs/resumo-jobfly.pdf"]);
const rows = pdf.stdout.split("\n").filter((line) => /^\s*\d+\s+\d+\s+image/.test(line));
check(`resumo em PDF tem a logo (${rows.length} imagem)`, pdf.status === 0 && rows.length >= 1, pdf.stderr);
check("HTML do resumo versionado com a logo", readFileSync(join(ROOT, "docs", "resumo", "resumo-jobfly.html"), "utf8").includes("../assets/logo.png"));
finish("LOGO_OK");
