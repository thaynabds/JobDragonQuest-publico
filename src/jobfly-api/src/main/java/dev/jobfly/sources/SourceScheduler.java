package dev.jobfly.sources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Executa a coleta das fontes automáticas em segundo plano, sem depender do painel aberto.
 * Desligue com {@code jobfly.sources.scheduler.enabled=false} (os testes fazem isso e chamam o coletor diretamente).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "jobfly.sources.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
class SourceScheduler {

    private static final Logger log = LoggerFactory.getLogger(SourceScheduler.class);

    private final SourceCollector collector;

    SourceScheduler(SourceCollector collector) {
        this.collector = collector;
    }

    @Scheduled(initialDelayString = "${jobfly.sources.scheduler.initial-delay:PT30S}",
            fixedDelayString = "${jobfly.sources.scheduler.interval:PT1M}")
    void tick() {
        try {
            int collected = collector.runDue();
            if (collected > 0) {
                log.info("Agendador coletou {} fonte(s)", collected);
            }
        } catch (RuntimeException ex) {
            // Um ciclo com erro não pode parar o agendador: registra e tenta no próximo.
            log.error("Ciclo do agendador de fontes falhou", ex);
        }
    }
}
