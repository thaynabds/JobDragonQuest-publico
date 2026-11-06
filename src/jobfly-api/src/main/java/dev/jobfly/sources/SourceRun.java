package dev.jobfly.sources;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jobfly.common.persistence.AssignedIdEntity;
import dev.jobfly.sources.WatchedSource.RunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Histórico de cada coleta ou conferência de uma fonte. */
@Entity
@Table(name = "source_run")
public class SourceRun extends AssignedIdEntity {

    public enum Trigger {
        SCHEDULED,
        MANUAL
    }

    @Id
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private Trigger trigger;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RunStatus status;

    @Column(nullable = false)
    private int found;

    @Column(nullable = false)
    private int created;

    @Column(nullable = false)
    private int recommended;

    @Column(length = 500)
    private String message;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    protected SourceRun() {
    }

    SourceRun(UUID sourceId, Trigger trigger, RunStatus status, int found, int created, int recommended, String message,
            Instant startedAt, Instant finishedAt) {
        this.id = UUID.randomUUID();
        this.sourceId = sourceId;
        this.trigger = trigger;
        this.status = status;
        this.found = found;
        this.created = created;
        this.recommended = recommended;
        this.message = message;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public RunStatus getStatus() {
        return status;
    }

    public int getFound() {
        return found;
    }

    public int getCreated() {
        return created;
    }

    public int getRecommended() {
        return recommended;
    }

    public String getMessage() {
        return message;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
