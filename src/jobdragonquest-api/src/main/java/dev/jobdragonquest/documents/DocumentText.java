package dev.jobdragonquest.documents;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
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
 * pessoa e sem serviço pago: PDF com Apache PDFBox (até 20 páginas), ODT, ODS e planilhas .xlsx, Word .docx lendo o XML de dentro do arquivo (com
 * limite contra zip-bomba), texto puro e imagem com o Tesseract, se ele estiver instalado.
 */
public final class DocumentText {

    static final int MAX_TEXT = 100_000;
    static final int MAX_PDF_PAGES = 20;
    static final long MAX_PDF_MEMORY = 64L * 1024 * 1024;
    static final long MAX_DOCX_XML = 10L * 1024 * 1024;
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern SHARED = Pattern.compile("(?s)<si\\b[^>]*>(.*?)</si>");
    private static final Pattern ROW = Pattern.compile("(?s)<row\\b[^>]*>(.*?)</row>");
    private static final Pattern CELL = Pattern.compile("(?s)<c\\b([^>]*?)(?:/>|>(.*?)</c>)");
    private static final Pattern VALUE = Pattern.compile("(?s)<v>(.*?)</v>");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n+");

    enum Type {
        PDF("application/pdf"),
        DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        ODT("application/vnd.oasis.opendocument.text"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        ODS("application/vnd.oasis.opendocument.spreadsheet"),
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
        if (startsWith(data, new byte[] {'P', 'K', 3, 4})) {
            if (hasEntry(data, "word/document.xml")) {
                return Type.DOCX;
            }
            if (hasEntry(data, "xl/workbook.xml")) {
                return Type.XLSX;
            }
            if (hasEntry(data, "content.xml")) {
                String mime = entries(data, "mimetype"::equals).getOrDefault("mimetype", "");
                if (mime.contains("opendocument.text")) {
                    return Type.ODT;
                }
                if (mime.contains("opendocument.spreadsheet")) {
                    return Type.ODS;
                }
            }
        }
        if (isText(data) && (fileName == null || fileName.toLowerCase().matches(".*\\.(txt|md|markdown|csv|tsv)$") || fileName.indexOf('.') < 0)) {
            return Type.TEXT;
        }
        throw DomainException.badRequest("document_type", "Tipo de arquivo não aceito: use PDF, Word (.docx), OpenDocument (.odt), planilha (.xlsx, .ods, .csv), texto, Markdown, PNG ou JPG.");
    }

    /** Texto do arquivo. Imagem sem Tesseract instalado volta vazia: a IA da pessoa lê a imagem pelo MCP. */
    static String extract(Type type, byte[] data) {
        String text = switch (type) {
            case PDF -> pdf(data);
            case DOCX -> docx(data);
            case ODT, ODS -> openDocument(data);
            case XLSX -> xlsx(data);
            case TEXT -> new String(data, StandardCharsets.UTF_8);
            case PNG, JPEG -> ocr(data, type);
        };
        return clean(text);
    }

    /** Texto de um PDF, com os mesmos limites do envio de documentos. */
    public static String pdfText(byte[] data) {
        return pdf(data);
    }

    private static String pdf(byte[] data) {
        // Memória limitada: um PDF pequeno pode descompactar para muito mais (bomba de descompressão).
        try (PDDocument document = Loader.loadPDF(data, "", null, null, MemoryUsageSetting.setupMainMemoryOnly(MAX_PDF_MEMORY).streamCache)) {
            if (document.isEncrypted()) {
                throw DomainException.badRequest("document_encrypted", "O PDF está protegido por senha.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(MAX_PDF_PAGES);
            // Na ordem visual da página: sem isso, currículo com blocos e colunas sai embaralhado.
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof DomainException domain) {
                throw domain;
            }
            throw DomainException.badRequest("document_unreadable", "Não foi possível ler o PDF.");
        }
    }

    /** O prefixo do XML varia conforme o programa que gerou o arquivo ({@code w:}, {@code ns0:}...). */
    private static String docx(byte[] data) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    byte[] xml = readLimited(zip, MAX_DOCX_XML);
                    String text = new String(xml, StandardCharsets.UTF_8)
                            .replaceAll("(?s)<(\\w+:)?Fallback\\b.*?</(\\w+:)?Fallback>", "")
                            .replaceAll("</(\\w+:)?p>", "\n").replaceAll("<(\\w+:)?tab\\s*/>", "\t").replaceAll("<(\\w+:)?br\\b[^>]*/>", "\n");
                    return HtmlUtils.htmlUnescape(TAGS.matcher(text).replaceAll(""));
                }
            }
        } catch (IOException ex) {
            throw DomainException.badRequest("document_unreadable", "Não foi possível ler o arquivo do Word.");
        }
        throw DomainException.badRequest("document_unreadable", "Arquivo do Word sem conteúdo.");
    }

    /** Texto de ODT e ODS: parágrafos em linhas, células separadas por tabulação. */
    private static String openDocument(byte[] data) {
        String xml = entries(data, "content.xml"::equals).get("content.xml");
        if (xml == null) {
            throw DomainException.badRequest("document_unreadable", "Arquivo sem conteúdo.");
        }
        String text = xml.replaceAll("(?s)<office:scripts\\b.*?</office:scripts>", "").replaceAll("(?s)<office:font-face-decls\\b.*?</office:font-face-decls>", "")
                .replaceAll("(?s)<office:automatic-styles\\b.*?</office:automatic-styles>", "")
                .replaceAll("</text:(p|h)>", "\n").replaceAll("<text:tab\\s*/>", "\t").replaceAll("<text:line-break\\s*/>", "\n")
                .replaceAll("<text:s\\b[^>]*/>", " ").replaceAll("</table:table-cell>", "\t").replaceAll("</table:table-row>", "\n");
        return HtmlUtils.htmlUnescape(TAGS.matcher(text).replaceAll(""));
    }

    /** Texto de planilha do Excel: cada linha da planilha numa linha, células separadas por tabulação. */
    private static String xlsx(byte[] data) {
        Map<String, String> files = entries(data, name -> name.equals("xl/sharedStrings.xml") || name.matches("xl/worksheets/[^/]+\\.xml"));
        List<String> shared = new ArrayList<>();
        Matcher item = SHARED.matcher(files.getOrDefault("xl/sharedStrings.xml", ""));
        while (item.find()) {
            shared.add(HtmlUtils.htmlUnescape(TAGS.matcher(item.group(1)).replaceAll("")));
        }
        StringBuilder out = new StringBuilder();
        files.entrySet().stream().filter(e -> e.getKey().startsWith("xl/worksheets/")).sorted(Map.Entry.comparingByKey()).forEach(sheet -> {
            Matcher row = ROW.matcher(sheet.getValue());
            while (row.find()) {
                List<String> cells = new ArrayList<>();
                Matcher cell = CELL.matcher(row.group(1));
                while (cell.find()) {
                    cells.add(cellText(cell.group(1), cell.group(2), shared));
                }
                out.append(String.join("\t", cells)).append('\n');
            }
            out.append('\n');
        });
        return out.toString();
    }

    private static String cellText(String attributes, String body, List<String> shared) {
        if (body == null) {
            return "";
        }
        if (attributes.contains("t=\"s\"")) {
            Matcher value = VALUE.matcher(body);
            if (value.find()) {
                try {
                    int index = Integer.parseInt(value.group(1).strip());
                    return index >= 0 && index < shared.size() ? shared.get(index) : "";
                } catch (NumberFormatException ex) {
                    return "";
                }
            }
            return "";
        }
        Matcher value = VALUE.matcher(body);
        return value.find() ? HtmlUtils.htmlUnescape(value.group(1)) : HtmlUtils.htmlUnescape(TAGS.matcher(body).replaceAll(""));
    }

    /** Lê só as entradas pedidas do ZIP, com limite por entrada, no total e na quantidade (zip-bomba). */
    private static Map<String, String> entries(byte[] data, Predicate<String> wanted) {
        Map<String, String> found = new HashMap<>();
        long total = 0;
        int seen = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && seen++ < 2000) {
                if (wanted.test(entry.getName())) {
                    byte[] bytes = readLimited(zip, MAX_DOCX_XML);
                    total += bytes.length;
                    if (total > MAX_DOCX_XML * 3) {
                        throw DomainException.badRequest("document_too_large", "O conteúdo do arquivo é grande demais.");
                    }
                    found.put(entry.getName(), new String(bytes, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException ex) {
            throw DomainException.badRequest("document_unreadable", "Não foi possível ler o arquivo.");
        }
        return found;
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
