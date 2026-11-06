package dev.jobfly.applications;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobfly.applications")
public record ApplicationProperties(int staleAfterDays, int duplicateWindowDays, BigDecimal minAutomaticConfidence) {

    public ApplicationProperties {
        if (staleAfterDays < 1 || duplicateWindowDays < 1) {
            throw new IllegalStateException("jobfly.applications: prazos precisam ser de pelo menos 1 dia");
        }
        if (minAutomaticConfidence == null || minAutomaticConfidence.signum() < 0
                || minAutomaticConfidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("jobfly.applications.min-automatic-confidence precisa estar entre 0 e 1");
        }
    }

    public Duration staleAfter() {
        return Duration.ofDays(staleAfterDays);
    }

    public Duration duplicateWindow() {
        return Duration.ofDays(duplicateWindowDays);
    }
}
