package dev.jobdragonquest.jobs.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.jobdragonquest.common.error.DomainException;

/** Proteção contra SSRF ao abrir links colados pela pessoa (issue #114). */
class SafeWebFetcherTest {

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1/vaga", "http://localhost:80/", "http://10.0.0.5/", "http://192.168.0.1/",
            "http://172.16.3.4/", "http://169.254.169.254/latest/meta-data/", "http://[::1]/", "http://0.0.0.0/",
            "http://100.64.0.1/", "http://[fd00::1]/", "http://[::ffff:127.0.0.1]/", "ftp://example.com/vaga",
            "file:///etc/passwd", "https://example.com:8443/vaga", "http://usuario:senha@example.com/", "javascript:alert(1)"})
    void blocksInternalOrUnsafeDestinations(String url) {
        assertThatThrownBy(() -> SafeWebFetcher.checkDestination(URI.create(url)))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).code()).isEqualTo("link_blocked"));
    }

    @Test
    void classifiesAddresses() throws Exception {
        assertThat(SafeWebFetcher.isInternal(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThat(SafeWebFetcher.isInternal(InetAddress.getByName("2001:4860:4860::8888"))).isFalse();
        assertThat(SafeWebFetcher.isInternal(InetAddress.getByName("100.127.255.255"))).isTrue();
        assertThat(SafeWebFetcher.isInternal(InetAddress.getByName("224.0.0.1"))).isTrue();
        assertThat(SafeWebFetcher.isInternal(InetAddress.getByName("::ffff:10.1.2.3"))).isTrue();
    }

    @Test
    void publicAddressWithDefaultPortPasses() {
        // Literal de IP público: não depende de DNS.
        SafeWebFetcher.checkDestination(URI.create("https://8.8.8.8/vaga"));
        SafeWebFetcher.checkDestination(URI.create("http://8.8.8.8:80/vaga"));
    }
}
