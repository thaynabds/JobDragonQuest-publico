package dev.jobdragonquest.audit;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final int MAX_DETAILS = 500;

    private final AuditRepository repository;
    private final Clock clock;

    public AuditService(AuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Participa da transação de quem chama: se a operação falhar, o registro também é desfeito. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID userId, String action, String entityType, UUID entityId, String details) {
        String safeDetails = details == null || details.length() <= MAX_DETAILS ? details : details.substring(0, MAX_DETAILS);
        repository.save(new AuditEntry(userId, action, entityType, entityId, safeDetails, clock.instant()));
    }

    /** Para eventos que precisam ficar registrados mesmo quando a operação falha (ex.: login recusado). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(UUID userId, String action, String details) {
        repository.save(new AuditEntry(userId, action, null, null, details, clock.instant()));
    }
}
