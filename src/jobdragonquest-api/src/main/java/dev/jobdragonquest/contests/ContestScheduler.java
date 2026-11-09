package dev.jobdragonquest.contests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Rodada de concursos em intervalo longo, para não sobrecarregar sites oficiais nem o servidor gratuito. */
@Component
@ConditionalOnProperty(prefix = "jobdragonquest.sources.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
class ContestScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContestScheduler.class);

    private final ContestScanner scanner;

    ContestScheduler(ContestScanner scanner) {
        this.scanner = scanner;
    }

    @Scheduled(initialDelayString = "${jobdragonquest.contests.initial-delay:PT10M}", fixedDelayString = "${jobdragonquest.contests.interval:PT6H}")
    void tick() {
        try {
            scanner.scanAll();
        } catch (RuntimeException ex) {
            log.error("Ciclo de concursos falhou", ex);
        }
    }
}
