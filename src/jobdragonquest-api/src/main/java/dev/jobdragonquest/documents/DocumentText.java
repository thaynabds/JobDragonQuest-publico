package dev.jobdragonquest.documents;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.util.HtmlUtils;

import dev.jobdragonquest.common.error.DomainException;

/**
 * Identifica o tipo real do arquivo pelos primeiros bytes (nunca pelo nome) e tira o texto dele, tudo no computador da
 * pessoa e sem serviço pago: PDF com Apache PDFBox (até 20 páginas), Word .docx lendo o XML de dentro do arquivo (com
 * limite contra zip-bomba), texto puro e imagem com o Tesseract, se ele estiver instalado.
 */
final class DocumentText {

    static final int MAX_TEXT = 100_000;
    static final int MAX_PDF_PAGES = 20;
    static final long MAX_PDF_MEMORY = 64L * 1024 * 1024;
    static final long MAX_DOCX_XML = 10L * 1024 * 1024;
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n+");

    enum Type {
        PDF("application/pdf"),
        DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        TEXT("text/plain; charset=utf-8"),
        PNG("image/png"),
        JPEG("image/jpeg");

        final String contentType;

        Type(String contentType) {
            this.contentType = contentType;
        }

        boolean isImage() {
            return this == PNG || this == JPEG;
        }
    }

    private DocumentText() {
    }

    /** Tipo pelo conteúdo. Arquivo que não é de nenhum dos tipos aceitos é recusado. */
    static Type detect(byte[] data, String fileName) {
        if (startsWith(data, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return Type.PDF;
        }
        if (startsWith(data, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return Type.PNG;
        }
        if (startsWith(data, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return Type.JPEG;
        }
        if (startsWith(data, new byte[] {'P', 'K', 3, 4}) && hasEntry(data, "word/document.xml")) {
            return Type.DOCX;
        }
        if (isText(data) && (fileName == null || fileName.toLowerCase().matches(".*\\.(txt|md|csv)$") || fileName.indexOf('.') < 0)) {
            return Type.TEXT;
        }
        throw DomainException.badRequest("document_type", "Tipo de arquivo não aceito: use PDF, Word (.docx), texto, PNG ou JPG.");
    }

    /** Texto do arquivo. Imagem sem Tesseract instalado volta vazia: a IA da pessoa lê a imagem pelo MCP. */
    static String extract(Type type, byte[] data) {
        String text = switch (type) {
            case PDF -> pdf(data);
            case DOCX -> docx(data);
            case TEXT -> new String(data, StandardCharsets.UTF_8);
            case PNG, JPEG -> ocr(data, type);
        };
        return clean(text);
    }

    private static String pdf(byte[] data) {
        // Memória limitada: um PDF pequeno pode descompactar para muito mais (bomba de descompressão).
        try (PDDocument document = Loader.loadPDF(data, "", null, null, MemoryUsageSetting.setupMainMemoryOnly(MAX_PDF_MEMORY).streamCache)) {
            if (document.isEncrypted()) {
                throw DomainException.badRequest("document_encrypted", "O PDF está protegido por senha.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(MAX_PDF_PAGES);
            return stripper.getText(document);
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof DomainException domain) {
                throw domain;
            }
            throw DomainException.badRequest("document_unreadable", "Não foi possível ler o PDF.");
        }
    }

    private static String docx(byte[] data) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    byte[] xml = readLimited(zip, MAX_DOCX_XML);
                    String text = new String(xml, StandardCharsets.UTF_8)
                            .replaceAll("</w:p>", "\n").replaceAll("<w:tab/>", "\t").replaceAll("<w:br/>", "\n");
                    return HtmlUtils.htmlUnescape(TAGS.matcher(text).replaceAll(""));
                }
            }
        } catch (IOException ex) {
            throw DomainException.badRequest("document_unreadable", "Não foi possível ler o arquivo do Word.");
        }
        throw DomainException.badRequest("document_unreadable", "Arquivo do Word sem conteúdo.");
    }

    /** OCR opcional com o Tesseract (gratuito, local). Sem ele, devolve vazio. Nada passa pelo shell. */
    private static String ocr(byte[] data, Type type) {
        Path input = null;
        try {
            input = Files.createTempFile("jdq-ocr-", type == Type.PNG ? ".png" : ".jpg");
            Files.write(input, data);
            Process process = new ProcessBuilder("tesseract", input.toString(), "stdout", "-l", "por+eng")
                    .redirectErrorStream(false).start();
            byte[] out;
            try (InputStream stdout = process.getInputStream()) {
                out = readLimited(stdout, MAX_TEXT * 4L);
            }
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            return process.exitValue() == 0 ? new String(out, StandardCharsets.UTF_8) : "";
        } catch (IOException ex) {
            return "";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "";
        } finally {
            if (input != null) {
                try {
                    Files.deleteIfExists(input);
                } catch (IOException ignored) {
                    // arquivo temporário: o sistema limpa depois
                }
            }
        }
    }

    static String clean(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replace('\u0000', ' ');
        cleaned = SPACES.matcher(cleaned).replaceAll(" ");
        cleaned = BLANK_LINES.matcher(cleaned).replaceAll("\n\n").strip();
        return cleaned.length() > MAX_TEXT ? cleaned.substring(0, MAX_TEXT) : cleaned;
    }

    private static byte[] readLimited(InputStream in, long max) throws IOException {
        byte[] bytes = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, max + 1));
        if (bytes.length > max) {
            throw DomainException.badRequest("document_too_large", "O conteúdo do arquivo é grande demais.");
        }
        return bytes;
    }

    private static boolean hasEntry(byte[] data, String name) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            int seen = 0;
            while ((entry = zip.getNextEntry()) != null && seen++ < 500) {
                if (name.equals(entry.getName())) {
                    return true;
                }
            }
        } catch (IOException ex) {
            return false;
        }
        return false;
    }

    /** Texto: sem bytes nulos nem caracteres de controle fora de tab e quebra de linha. */
    private static boolean isText(byte[] data) {
        int sample = Math.min(data.length, 4096);
        for (int i = 0; i < sample; i++) {
            int b = data[i] & 0xff;
            if (b < 0x09 || (b > 0x0D && b < 0x20) || b == 0x7F) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
