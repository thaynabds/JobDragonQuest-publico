package dev.jobdragonquest.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Issue #172: Word gerado por outros programas usa outro prefixo no XML e tem de manter as linhas. */
class DocumentTextDocxTest {

    private static byte[] docx(String p) throws Exception {
        String xml = "<%1$s:document><%1$s:p><%1$s:r><%1$s:t>Linha um</%1$s:t></%1$s:r></%1$s:p><%1$s:p><%1$s:r><%1$s:t>Linha</%1$s:t><%1$s:br %1$s:type=\"page\"/><%1$s:t>dois</%1$s:t></%1$s:r></%1$s:p></%1$s:document>".formatted(p);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    @ParameterizedTest
    @ValueSource(strings = {"w", "ns0"})
    void keepsLinesWhateverThePrefix(String prefix) throws Exception {
        assertThat(DocumentText.extract(DocumentText.Type.DOCX, docx(prefix))).isEqualTo("Linha um\nLinha\ndois");
    }

    private static byte[] zip(String... nameThenContent) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int i = 0; i < nameThenContent.length; i += 2) {
                zip.putNextEntry(new ZipEntry(nameThenContent[i]));
                zip.write(nameThenContent[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @org.junit.jupiter.api.Test
    void readsOdtAndOds() throws Exception {
        byte[] odt = zip("mimetype", "application/vnd.oasis.opendocument.text", "content.xml",
                "<office:document-content><office:automatic-styles>x</office:automatic-styles><text:h>Título</text:h><text:p>A<text:s/>B &amp; C</text:p></office:document-content>");
        assertThat(DocumentText.detect(odt, "a.odt")).isEqualTo(DocumentText.Type.ODT);
        assertThat(DocumentText.extract(DocumentText.Type.ODT, odt)).isEqualTo("Título\nA B & C");
        byte[] ods = zip("mimetype", "application/vnd.oasis.opendocument.spreadsheet", "content.xml",
                "<x><table:table-row><table:table-cell><text:p>a</text:p></table:table-cell><table:table-cell><text:p>b</text:p></table:table-cell></table:table-row></x>");
        assertThat(DocumentText.detect(ods, "a.ods")).isEqualTo(DocumentText.Type.ODS);
        assertThat(DocumentText.extract(DocumentText.Type.ODS, ods)).contains("a").contains("b");
    }

    @org.junit.jupiter.api.Test
    void readsExcelWithSharedAndNumericCells() throws Exception {
        byte[] xlsx = zip("xl/workbook.xml", "<workbook/>", "xl/sharedStrings.xml", "<sst><si><t>Java</t></si><si><r><t>Spring </t></r><r><t>Boot</t></r></si></sst>",
                "xl/worksheets/sheet1.xml", "<sheetData><row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\"><v>42</v></c></row><row r=\"2\"><c r=\"A2\" t=\"s\"><v>1</v></c><c r=\"B2\" t=\"s\"><v>99</v></c></row></sheetData>");
        assertThat(DocumentText.detect(xlsx, "a.xlsx")).isEqualTo(DocumentText.Type.XLSX);
        assertThat(DocumentText.extract(DocumentText.Type.XLSX, xlsx)).isEqualTo("Java 42\nSpring Boot");
    }

    @org.junit.jupiter.api.Test
    void markdownIsText() {
        assertThat(DocumentText.detect("# Título".getBytes(StandardCharsets.UTF_8), "cv.md")).isEqualTo(DocumentText.Type.TEXT);
    }
}
