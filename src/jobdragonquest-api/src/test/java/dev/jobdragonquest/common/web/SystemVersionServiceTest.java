package dev.jobdragonquest.common.web;

import static org.assertj.core.api.Assertions.assertThat;
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

import dev.jobdragonquest.sources.collect.BoundedHttpFetcher;
import dev.jobdragonquest.sources.collect.CollectionException;

/** Issue #172: aviso de versão nova, com cache e sem quebrar quando o GitHub falha. */
class SystemVersionServiceTest {

    private final BoundedHttpFetcher fetcher = mock(BoundedHttpFetcher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);

    private void latest(String tag) {
        when(fetcher.get(any())).thenReturn(("{\"tag_name\":\"" + tag + "\",\"x\":1}").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void warnsOnlyWhenThereIsANewerVersionAndCaches() {
        latest("v0.7.0");
        var service = new SystemVersionService(fetcher, clock, "0.6.0");
        var info = service.info();
        assertThat(info.updateAvailable()).isTrue();
        assertThat(info.releaseUrl()).endsWith("/releases/tag/v0.7.0");
        service.info();
        verify(fetcher, times(1)).get(any());
        latest("v0.6.0");
        assertThat(new SystemVersionService(fetcher, clock, "0.6.0").info().updateAvailable()).isFalse();
    }

    @Test
    void comparesNumbersNotText() {
        assertThat(SystemVersionService.isNewer("0.10.0", "0.9.9")).isTrue();
        assertThat(SystemVersionService.isNewer("0.9.9", "0.10.0")).isFalse();
    }

    @Test
    void failureOrStrangeTagDoesNotWarn() {
        when(fetcher.get(any())).thenThrow(new CollectionException("sem rede"));
        assertThat(new SystemVersionService(fetcher, clock, "0.6.0").info().updateAvailable()).isFalse();
        BoundedHttpFetcher other = mock(BoundedHttpFetcher.class);
        when(other.get(any())).thenReturn("{\"tag_name\":\"<script>\"}".getBytes(StandardCharsets.UTF_8));
        var info = new SystemVersionService(other, clock, "0.6.0").info();
        assertThat(info.latest()).isNull();
        assertThat(info.releaseUrl()).isNull();
    }
}
