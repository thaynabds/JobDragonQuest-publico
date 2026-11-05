package dev.jobfly.applications;

import dev.jobfly.common.persistence.AssignedIdEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jobfly.applications.ApplicationEnums.EventKind;
import dev.jobfly.applications.ApplicationEnums.EventSource;
import dev.jobfly.applications.ApplicationEnums.EventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fato do histórico da candidatura, com origem, evidência e confiança (RN-03).
 * A evidência é texto não confiável (pode ter vindo de um e-mail) e por isso é exibida como texto, nunca como HTML.
 */
@Entity
@Table(name = "application_event")
public class ApplicationEvent extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EventKind kind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_state", length = 40)
    private ApplicationState fromState;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_state", length = 40)
    private ApplicationState toState;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EventSource source;

    @Column(nullable = false, length = 4000)
    private String evidence;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApplicationEvent() {
    }

    ApplicationEvent(UUID applicationId, EventKind kind, ApplicationState fromState, ApplicationState toState,
            EventSource source, String evidence, BigDecimal confidence, EventStatus status, Instant occurredAt, Instant now) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.kind = kind;
        this.fromState = fromState;
        this.toState = toState;
        this.source = source;
        this.evidence = evidence;
        this.confidence = confidence;
        this.status = status;
        this.occurredAt = occurredAt;
        this.createdAt = now;
    }

    void resolve(EventStatus resolution) {
        this.status = resolution;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public EventKind getKind() {
        return kind;
    }

    public ApplicationState getFromState() {
        return fromState;
    }

    public ApplicationState getToState() {
        return toState;
    }

    public EventSource getSource() {
        return source;
    }

    public String getEvidence() {
        return evidence;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
