package dev.jobdragonquest.common.web;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.sources.collect.BoundedHttpFetcher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cotações do dia para converter salários, das moedas de países que falam português, inglês ou espanhol. Consulta um
 * endereço fixo e público (só o código da moeda base, nenhum dado da pessoa) no máximo a cada 12 horas.
 */
@Service
public class CurrencyService {

    static final URI RATES = URI.create("https://open.er-api.com/v6/latest/USD");
    /** Moedas de países de língua portuguesa, inglesa e espanhola. */
    static final List<String> CURRENCIES = List.of(
            "BRL", "AOA", "MZN", "CVE", "STN", "XOF", "MOP", "EUR",
            "USD", "GBP", "CAD", "AUD", "NZD", "ZAR", "INR", "SGD", "NGN", "KES", "GHS", "PHP", "JMD", "TTD",
            "MXN", "ARS", "CLP", "COP", "PEN", "UYU", "BOB", "PYG", "VES", "GTQ", "HNL", "NIO", "CRC", "PAB", "DOP", "CUP", "XAF");
    private static final Duration CACHE = Duration.ofHours(12);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Quantas unidades de cada moeda valem 1 dólar. */
    public record Rates(String base, String updatedAt, Map<String, Double> rates) {
    }

    private final BoundedHttpFetcher fetcher;
    private final Clock clock;
    private volatile Rates cached;
    private volatile Instant checkedAt;

    CurrencyService(BoundedHttpFetcher fetcher, Clock clock) {
        this.fetcher = fetcher;
        this.clock = clock;
    }

    public synchronized Rates rates() {
        Instant now = clock.instant();
        if (cached != null && checkedAt.plus(CACHE).isAfter(now)) {
            return cached;
        }
        try {
            JsonNode root = JSON.readTree(fetcher.get(RATES));
            JsonNode values = root.path("rates");
            Map<String, Double> rates = new TreeMap<>();
            for (String code : CURRENCIES) {
                double value = values.path(code).asDouble(0);
                if (value > 0) {
                    rates.put(code, value);
                }
            }
            if (!"success".equals(root.path("result").asString("")) || !rates.containsKey("BRL")) {
                throw new IllegalStateException("resposta inesperada");
            }
            cached = new Rates("USD", root.path("time_last_update_utc").asString(""), rates);
            checkedAt = now;
            return cached;
        } catch (RuntimeException ex) {
            if (cached != null) {
                return cached;
            }
            throw new DomainException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "rates_unavailable", "Cotações indisponíveis agora. Tente mais tarde.");
        }
    }
}
