package dev.jobdragonquest.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.sources.collect.BoundedHttpFetcher;
import dev.jobdragonquest.sources.collect.CollectionException;

/** Issue #172: tradução automática com cache e sem quebrar o currículo quando o serviço falha. */
class TranslationServiceTest {

    private final BoundedHttpFetcher fetcher = mock(BoundedHttpFetcher.class);
    private final TranslationService service = new TranslationService(fetcher);

    private static byte[] body(int status, String text) {
        return ("{\"responseStatus\":" + status + ",\"responseData\":{\"translatedText\":\"" + text + "\"}}").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void translatesUnescapesAndCaches() {
        when(fetcher.get(any())).thenReturn(body(200, "I&#39;m a developer"));
        assertThat(service.translate("Sou desenvolvedora", "en")).isEqualTo("I'm a developer");
        assertThat(service.translate("Sou desenvolvedora", "en")).isEqualTo("I'm a developer");
        verify(fetcher, times(1)).get(any());
    }

    @Test
    void failureOrQuotaReturnsNull() {
        when(fetcher.get(any())).thenReturn(body(429, "QUOTA"));
        assertThat(service.translate("Texto", "en")).isNull();
        when(fetcher.get(any())).thenThrow(new CollectionException("sem rede"));
        assertThat(service.translate("Outro", "en")).isNull();
    }

    @Test
    void longTextIsSplitWithoutLosingAnything() {
        String text = "Frase de teste com várias palavras. ".repeat(40);
        var pieces = TranslationService.chunks(text);
        assertThat(pieces).allSatisfy(piece -> assertThat(piece.length()).isLessThanOrEqualTo(450));
        assertThat(String.join("", pieces)).isEqualTo(text);
    }
}
