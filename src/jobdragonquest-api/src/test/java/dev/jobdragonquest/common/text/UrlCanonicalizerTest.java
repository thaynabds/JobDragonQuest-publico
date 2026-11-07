package dev.jobdragonquest.common.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.jobdragonquest.common.error.DomainException;

class UrlCanonicalizerTest {

    @Test
    void removesTrackingFragmentWwwAndTrailingSlash() {
        String canonical = UrlCanonicalizer.canonicalize(
                "HTTPS://WWW.Empresa.com.br/vagas/123/?utm_source=linkedin&b=2&a=1&gclid=x#topo");
        assertThat(canonical).isEqualTo("https://empresa.com.br/vagas/123?a=1&b=2");
    }

    @Test
    void sameLinkedInJobFromDifferentUrlsHasSameKey() {
        String bySlug = UrlCanonicalizer.canonicalize("https://br.linkedin.com/jobs/view/desenvolvedor-java-junior-at-sensedia-4486065865?trk=abc");
        String byId = UrlCanonicalizer.canonicalize("https://www.linkedin.com/jobs/view/4486065865/");
        String byCollection = UrlCanonicalizer.canonicalize("https://www.linkedin.com/jobs/collections/recommended/?currentJobId=4486065865");
        assertThat(bySlug).isEqualTo(byId).isEqualTo(byCollection).isEqualTo("https://linkedin.com/jobs/view/4486065865");
        assertThat(UrlCanonicalizer.hash(bySlug)).hasSize(64).isEqualTo(UrlCanonicalizer.hash(byId));
    }

    @Test
    void keepsNonDefaultPort() {
        assertThat(UrlCanonicalizer.canonicalize("http://localhost:8080/x")).isEqualTo("http://localhost:8080/x");
        assertThat(UrlCanonicalizer.canonicalize("https://a.com:443/x")).isEqualTo("https://a.com/x");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "file:///etc/passwd",
            "ftp://a.com/x",
            "https://user:senha@a.com/x",
            "not a url",
            "//sem-esquema.com",
            "   "})
    void rejectsDangerousOrInvalidUrls(String url) {
        assertThatThrownBy(() -> UrlCanonicalizer.canonicalize(url))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code()).isEqualTo("invalid_url");
    }

    @Test
    void rejectsOverlongUrl() {
        assertThatThrownBy(() -> UrlCanonicalizer.canonicalize("https://a.com/" + "x".repeat(2100)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void optionalAcceptsBlank() {
        assertThat(UrlCanonicalizer.optional(null)).isNull();
        assertThat(UrlCanonicalizer.optional(" ")).isNull();
    }
}
