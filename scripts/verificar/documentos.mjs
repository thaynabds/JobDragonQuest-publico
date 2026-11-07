// G39: documentos do perfil (currículos com versões, carta, certificados) e anexos das candidaturas, guardados com
// segurança e isolados por conta; leitura de PDF, Word, texto, link e foto com proposta que a pessoa confirma.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { check, finish, ROOT, run } from "./lib.mjs";

const api = join(ROOT, "src", "jobdragonquest-api");
const web = join(ROOT, "src", "jobdragonquest-web");
const mcp = join(ROOT, "src", "jobdragonquest-mcp");
const main = join(api, "src/main/java/dev/jobdragonquest/documents");
const text = readFileSync(join(main, "DocumentText.java"), "utf8");
check("tipo pelo conteúdo, não pelo nome", text.includes("%PDF-") && text.includes("word/document.xml"));
check("limite contra zip-bomba e páginas de PDF", text.includes("MAX_DOCX_XML") && text.includes("setEndPage(MAX_PDF_PAGES)"));
check("OCR sem shell e com tempo limite", text.includes('new ProcessBuilder("tesseract"') && text.includes("waitFor(30, TimeUnit.SECONDS)"));
const service = readFileSync(join(main, "DocumentService.java"), "utf8");
check("limites por arquivo, perfil, candidatura e conta", ["MAX_BYTES", "MAX_PER_PROFILE", "MAX_PER_APPLICATION", "MAX_ACCOUNT_BYTES"].every((k) => service.includes(k)));
check("só a conta dona", service.includes("findByIdAndUserId(documentId, userId)"));
const controller = readFileSync(join(main, "DocumentController.java"), "utf8");
check("download seguro (anexo, nosniff, sandbox)", controller.includes("attachment()") && controller.includes("nosniff") && controller.includes("sandbox"));
const pom = readFileSync(join(api, "pom.xml"), "utf8");
check("PDFBox com versão fixa", /<artifactId>pdfbox<\/artifactId>\s*<version>\d+\.\d+\.\d+<\/version>/.test(pom));
const tools = readFileSync(join(mcp, "src/tools.ts"), "utf8");
check("MCP lê documento por conteúdo e acrescenta item", tools.includes("jobdragonquest_ler_documento") && tools.includes("conteudoBase64") && tools.includes("jobdragonquest_adicionar_item_perfil"));
for (const locale of ["pt-BR", "en", "es"]) {
  const msgs = readFileSync(join(web, "src/i18n/locales", `${locale}.ts`), "utf8");
  check(`textos de documentos em ${locale}`, ["documents.title", "documents.confirm", "error.document_type"].every((k) => msgs.includes(`"${k}"`)));
}
const apiTests = run("./mvnw", ["-q", "-B", "-Djacoco.skip=true", "-Dtest=DocumentTest,CertificateParserTest,RequestSizeLimitFilterTest", "-Dsurefire.failIfNoSpecifiedTests=true", "test"],
  { cwd: api, timeout: 1_200_000 });
check("testes de documentos na API passam", apiTests.status === 0, (apiTests.stdout + apiTests.stderr).slice(-400));
const report = readFileSync(join(api, "target/surefire-reports/TEST-dev.jobdragonquest.documents.DocumentTest.xml"), "utf8");
check("rodaram leitura de PDF, Word, isolamento e confirmação", ["readsCertificatePdf", "readsWordAndRefuses", "savesVersionedResumes", "appendsConfirmedEntry"].every((t) => report.includes(t)) && !/<failure|<error/.test(report));
const webTests = run("npx", ["vitest", "run", "src/pages/DocumentsSection.test.tsx"], { cwd: web, timeout: 600_000 });
check("testes de documentos no painel passam", webTests.status === 0, webTests.stdout.slice(-400));
const mcpTests = run("npx", ["vitest", "run", "--reporter=verbose"], { cwd: mcp, timeout: 600_000 });
check("testes do MCP passam", mcpTests.status === 0 && mcpTests.stdout.includes("ler documento recebe conteúdo"), mcpTests.stdout.slice(-400));
finish("DOCUMENTOS_OK");
