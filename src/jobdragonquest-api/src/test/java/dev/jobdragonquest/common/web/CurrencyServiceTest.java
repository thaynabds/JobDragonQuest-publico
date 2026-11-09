package dev.jobdragonquest.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.sources.collect.BoundedHttpFetcher;
import dev.jobdragonquest.sources.collect.CollectionException;

/** Issue #174: cotações com cache, só das moedas previstas, e erro claro quando o serviço está fora. */
class CurrencyServiceTest {

    private final BoundedHttpFetcher fetcher = mock(BoundedHttpFetcher.class);
    private final CurrencyService service = new CurrencyService(fetcher, Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void keepsOnlyKnownCurrenciesAndCaches() {
        when(fetcher.get(any())).thenReturn("{\"result\":\"success\",\"time_last_update_utc\":\"hoje\",\"rates\":{\"BRL\":5.5,\"AOA\":900,\"JPY\":150,\"CAD\":0}}".getBytes(StandardCharsets.UTF_8));
        var rates = service.rates();
        assertThat(rates.rates()).containsOnlyKeys("BRL", "AOA");
        service.rates();
        verify(fetcher, times(1)).get(any());
    }

    @Test
    void failureGivesAClearError() {
        when(fetcher.get(any())).thenThrow(new CollectionException("sem rede"));
        assertThatThrownBy(service::rates).isInstanceOf(DomainException.class);
    }
}
