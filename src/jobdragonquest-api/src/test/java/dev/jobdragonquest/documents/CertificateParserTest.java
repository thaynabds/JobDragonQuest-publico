package dev.jobdragonquest.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class CertificateParserTest {

    @Test
    void englishCertificateWithMonthNameDate() {
        CertificateParser.Proposal p = CertificateParser.parse("""
                Certificate of Completion
                This is to certify that Test Person has successfully completed Cloud Fundamentals
                Coursera  ·  Oct 3, 2026  ·  Credential ID ABC123XYZ9""", List.of());
        assertThat(p.title()).isEqualTo("Cloud Fundamentals");
        assertThat(p.organization()).isEqualTo("Coursera");
        assertThat(p.date()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(p.credentialId()).isEqualTo("ABC123XYZ9");
        assertThat(p.kind()).isEqualTo("CERTIFICATION");
    }

    @Test
    void missingDataStaysEmptyAndInvalidDatesAreIgnored() {
        CertificateParser.Proposal p = CertificateParser.parse("Um texto qualquer sem certificado. 31/02/2026", List.of());
        assertThat(p.title()).isNull();
        assertThat(p.organization()).isNull();
        assertThat(p.date()).isNull();
        assertThat(p.hours()).isNull();
        assertThat(CertificateParser.parse("concluiu o curso de Git em 01/01/2999", List.of()).date()).isNull();
    }

    @Test
    void safeNameStripsPathsAndUsesRealExtension() {
        assertThat(DocumentService.safeName("..\\..\\windows\\evil.exe", DocumentText.Type.PDF)).isEqualTo("evil.pdf");
        assertThat(DocumentService.safeName("\"<script>\".pdf", DocumentText.Type.PDF)).isEqualTo("script.pdf");
        assertThat(DocumentService.safeName(".bashrc", DocumentText.Type.TEXT)).isEqualTo("documento.txt");
    }
}
