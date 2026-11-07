package dev.jobdragonquest.documents;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import dev.jobdragonquest.ApiTestSupport;

/** Documentos e leitura de certificados (issue #124). */
class DocumentTest extends ApiTestSupport {

    static byte[] pdf(String... lines) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                for (String line : lines) {
                    content.showText(line);
                    content.newLineAtOffset(0, -18);
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    static byte[] docx(String... paragraphs) throws Exception {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\"?><w:document xmlns:w=\"x\"><w:body>");
        for (String p : paragraphs) {
            xml.append("<w:p><w:r><w:t>").append(p.replace("&", "&amp;")).append("</w:t></w:r></w:p>");
        }
        xml.append("</w:body></w:document>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.toString().getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private ResultActions upload(String token, String path, MockMultipartFile file, String kind) throws Exception {
        var request = multipart(path).file(file).header("Authorization", "Bearer " + token);
        if (kind != null) {
            request.param("kind", kind);
        }
        return mvc.perform(request);
    }

    @Test
    void readsCertificatePdfAndProposesAnEntryWithoutSaving() throws Exception {
        String token = newUser();
        byte[] certificate = pdf("Certificado de Conclusao", "Certificamos que Pessoa Teste concluiu o curso de Java e Spring Boot",
                "com carga horaria de 40 horas, em 15/10/2026.", "Udemy - Certificado no: UC-1234abcd-5678", "https://ude.my/UC-1234abcd-5678");
        upload(token, "/api/v1/documents/read", new MockMultipartFile("file", "certificado.pdf", "application/pdf", certificate), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PDF"))
                .andExpect(jsonPath("$.proposal.kind").value("COURSE"))
                .andExpect(jsonPath("$.proposal.title").value("Java e Spring Boot"))
                .andExpect(jsonPath("$.proposal.organization").value("Udemy"))
                .andExpect(jsonPath("$.proposal.hours").value(40))
                .andExpect(jsonPath("$.proposal.date").value("2026-10-15"))
                .andExpect(jsonPath("$.proposal.credentialId").value("UC-1234abcd-5678"))
                .andExpect(jsonPath("$.proposal.skills").value(hasItem("java")));
    }

    @Test
    void readsWordAndRefusesFakeOrDangerousFiles() throws Exception {
        String token = newUser();
        upload(token, "/api/v1/documents/read", new MockMultipartFile("file", "cv.docx", "application/octet-stream",
                docx("Experiência", "Desenvolvedora Java na Empresa A", "Python & SQL")), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DOCX"))
                .andExpect(jsonPath("$.text").value(containsString("Python & SQL")));
        // Nome de PDF, conteúdo de HTML: recusado pelo conteúdo.
        upload(token, "/api/v1/documents/read", new MockMultipartFile("file", "x.pdf", "application/pdf",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("document_type"));
        upload(token, "/api/v1/documents/read", new MockMultipartFile("file", "a.exe", "application/octet-stream", new byte[] {'M', 'Z', 0, 0}), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("document_type"));
        upload(token, "/api/v1/documents/read", new MockMultipartFile("file", "big.txt", "text/plain", new byte[DocumentService.MAX_BYTES + 1]), null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void savesVersionedResumesAndAttachmentsOnlyForTheOwner() throws Exception {
        String token = newUser();
        String profile = read(postJson(token, "/api/v1/profiles", "{\"name\":\"Java\",\"seniority\":\"JUNIOR\"}"), "$.id");
        String application = read(postJson(token, "/api/v1/applications", """
                {"origin":"MANUAL","company":"Docs SA","title":"Dev"}""").andExpect(status().isCreated()), "$.id");
        byte[] cv = pdf("Pessoa Teste", "Desenvolvedora Java");

        upload(token, "/api/v1/profiles/" + profile + "/documents", new MockMultipartFile("file", "../../etc/cv<b>.pdf", "application/pdf", cv), "RESUME")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.name").value("cvb.pdf"));
        String second = read(upload(token, "/api/v1/profiles/" + profile + "/documents", new MockMultipartFile("file", "cv2.pdf", "application/pdf", cv), "RESUME")
                .andExpect(jsonPath("$.version").value(2)), "$.id");
        getJson(token, "/api/v1/profiles/" + profile + "/documents").andExpect(jsonPath("$.length()").value(2));
        upload(token, "/api/v1/applications/" + application + "/documents", new MockMultipartFile("file", "portfolio.pdf", "application/pdf", cv), null)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("ATTACHMENT"));

        mvc.perform(get("/api/v1/documents/" + second + "/download").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")));
        getJson(token, "/api/v1/documents/" + second + "/read").andExpect(jsonPath("$.text").value(containsString("Desenvolvedora Java")));

        String intruder = newUser();
        mvc.perform(get("/api/v1/documents/" + second + "/download").header("Authorization", "Bearer " + intruder)).andExpect(status().isNotFound());
        getJson(intruder, "/api/v1/profiles/" + profile + "/documents").andExpect(status().isNotFound());
        upload(intruder, "/api/v1/applications/" + application + "/documents", new MockMultipartFile("file", "x.pdf", "application/pdf", cv), null)
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/documents/" + second).header("Authorization", "Bearer " + intruder)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/documents/" + second).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        upload(token, "/api/v1/applications/" + application + "/documents", new MockMultipartFile("file", "x.pdf", "application/pdf", cv), "RESUME")
                .andExpect(status().isCreated());
    }

    @Test
    void appendsConfirmedEntryToTheProfile() throws Exception {
        String token = newUser();
        String profile = read(postJson(token, "/api/v1/profiles", "{\"name\":\"Java\",\"seniority\":\"JUNIOR\"}"), "$.id");
        postJson(token, "/api/v1/profiles/" + profile + "/entries", """
                {"kind":"COURSE","title":"Java e Spring Boot","organization":"Udemy","endDate":"2026-10-15","hours":40,
                 "credentialId":"UC-1234abcd-5678","url":"https://ude.my/UC-1234abcd-5678","status":"COMPLETED"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].title").value("Java e Spring Boot"));
        postJson(newUser(), "/api/v1/profiles/" + profile + "/entries", "{\"kind\":\"COURSE\",\"title\":\"X\"}").andExpect(status().isNotFound());
    }
}
