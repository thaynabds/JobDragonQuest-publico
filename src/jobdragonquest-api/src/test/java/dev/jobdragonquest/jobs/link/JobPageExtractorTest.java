package dev.jobdragonquest.jobs.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.common.domain.WorkMode;

/** Extração dos dados da vaga a partir do HTML (issue #114). */
class JobPageExtractorTest {

    private static final URI PAGE = URI.create("https://www.empresa.test/vagas/42");

    @Test
    void readsJobPostingFromJsonLdInsideGraph() {
        String html = """
                <html><head><script type="application/ld+json">{"@context":"https://schema.org","@graph":[
                  {"@type":"Organization","name":"Outra"},
                  {"@type":"JobPosting","title":"Desenvolvedora Java Júnior","hiringOrganization":{"name":"Empresa Teste"},
                   "description":"<p>Java &amp; Spring Boot</p><script>alert(1)<\\/script>","jobLocationType":"TELECOMMUTE",
                   "jobLocation":{"address":{"addressLocality":"Recife","addressRegion":"PE","addressCountry":"BR"}},
                   "baseSalary":{"currency":"brl","value":{"minValue":4000,"maxValue":6000,"unitText":"MONTH"}}}]}
                </script></head><body>x</body></html>""";
        JobPageExtractor.Extracted data = JobPageExtractor.extract(html, PAGE);
        assertThat(data.from()).isEqualTo("JSON_LD");
        assertThat(data.title()).isEqualTo("Desenvolvedora Java Júnior");
        assertThat(data.company()).isEqualTo("Empresa Teste");
        assertThat(data.location()).isEqualTo("Recife, PE");
        assertThat(data.country()).isEqualTo("BR");
        assertThat(data.workMode()).isEqualTo(WorkMode.REMOTE);
        assertThat(data.salaryMin()).isEqualByComparingTo(new BigDecimal("4000"));
        assertThat(data.salaryMax()).isEqualByComparingTo(new BigDecimal("6000"));
        assertThat(data.salaryCurrency()).isEqualTo("BRL");
        assertThat(data.salaryPeriod()).isEqualTo("MONTH");
        // HTML vira texto: nenhuma tag sobra.
        assertThat(data.description()).contains("Java & Spring Boot").doesNotContain("<");
    }

    @Test
    void ignoresBrokenJsonAndInvalidSalaryAndFallsBackToMetaTags() {
        String broken = """
                <script type="application/ld+json">{ isto não é json </script>
                <script type="application/ld+json">{"@type":"JobPosting","title":"Dev","hiringOrganization":"Acme",
                 "baseSalary":{"currency":"BRL","value":{"minValue":9000,"maxValue":100}}}</script>""";
        JobPageExtractor.Extracted data = JobPageExtractor.extract(broken, PAGE);
        assertThat(data.title()).isEqualTo("Dev");
        assertThat(data.company()).isEqualTo("Acme");
        assertThat(data.salaryMin()).isNull();

        String meta = """
                <html><head><title>Ignorado</title><meta content="Analista de Dados" property="og:title">
                <meta property='og:site_name' content='Loja X'><meta name="description" content="Vaga &lt;b&gt;remota&lt;/b&gt;"></head></html>""";
        JobPageExtractor.Extracted fallback = JobPageExtractor.extract(meta, PAGE);
        assertThat(fallback.from()).isEqualTo("META");
        assertThat(fallback.title()).isEqualTo("Analista de Dados");
        assertThat(fallback.company()).isEqualTo("Loja X");
        assertThat(fallback.description()).isEqualTo("Vaga remota");

        JobPageExtractor.Extracted bare = JobPageExtractor.extract("<html><title> Só o título </title></html>", PAGE);
        assertThat(bare.title()).isEqualTo("Só o título");
        assertThat(bare.company()).isEqualTo("empresa.test");
    }
}
