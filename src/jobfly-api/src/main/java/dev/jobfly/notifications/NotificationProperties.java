package dev.jobfly.notifications;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code refreshInterval}: intervalo mínimo entre duas verificações de pendências da mesma pessoa (o painel consulta a
 * cada poucos segundos). {@code retention}: avisos lidos mais antigos que isto são apagados.
 */
@ConfigurationProperties(prefix = "jobfly.notifications")
public record NotificationProperties(Duration refreshInterval, Duration retention) {

    public NotificationProperties {
        refreshInterval = refreshInterval == null ? Duration.ofSeconds(30) : refreshInterval;
        retention = retention == null ? Duration.ofDays(90) : retention;
        if (refreshInterval.isNegative() || retention.compareTo(Duration.ofDays(1)) < 0) {
            throw new IllegalStateException("jobfly.notifications: intervalo negativo ou retenção menor que 1 dia");
        }
    }
}
