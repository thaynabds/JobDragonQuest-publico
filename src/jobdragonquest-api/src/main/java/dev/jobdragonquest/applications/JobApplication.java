package dev.jobdragonquest.applications;

import dev.jobdragonquest.common.persistence.AssignedIdEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jobdragonquest.applications.ApplicationEnums.Origin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Candidatura de qualquer origem. Guarda cópias de empresa, cargo e URL para que uma candidatura externa
 * (LinkedIn, site da empresa, Instagram...) seja acompanhada mesmo sem vaga cadastrada (seção 23).
 */
@Entity
@Table(name = "job_application")
public class JobApplication extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "profile_id")
    private UUID profileId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Origin origin;

    @Column(length = 80)
    private String platform;

    @Column(nullable = false, length = 200)
    private String company;

    @Column(name = "company_norm", nullable = false, length = 200)
    private String companyNorm;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "title_norm", nullable = false, length = 200)
    private String titleNorm;

    @Column(name = "job_url", length = 2048)
    private String jobUrl;

    @Column(name = "url_hash", length = 64)
    private String urlHash;

    @Column(length = 2)
    private String country;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private ApplicationState state;

    @Column(name = "state_changed_at", nullable = false)
    private Instant stateChangedAt;

    @Column(name = "last_evidence_at", nullable = false)
    private Instant lastEvidenceAt;

    @Column(name = "next_action", length = 500)
    private String nextAction;

    @Column(name = "next_due_at")
    private Instant nextDueAt;

    @Column(name = "match_score")
    private Integer matchScore;

    @Column(length = 4000)
    private String notes;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected JobApplication() {
    }

    JobApplication(UUID userId, Details details, Origin origin, ApplicationState state, Integer matchScore,
            String idempotencyKey, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.origin = origin;
        this.state = state;
        this.stateChangedAt = now;
        this.lastEvidenceAt = now;
        this.matchScore = matchScore;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = now;
        apply(details, now);
    }

    void apply(Details details, Instant now) {
        this.jobId = details.jobId();
        this.profileId = details.profileId();
        this.platform = details.platform();
        this.company = details.company();
        this.companyNorm = details.companyNorm();
        this.title = details.title();
        this.titleNorm = details.titleNorm();
        this.jobUrl = details.jobUrl();
        this.urlHash = details.urlHash();
        this.country = details.country();
        this.appliedAt = details.appliedAt();
        this.notes = details.notes();
        this.nextAction = details.nextAction();
        this.nextDueAt = details.nextDueAt();
        this.updatedAt = now;
    }

    void moveTo(ApplicationState target, Instant occurredAt, Instant now) {
        this.state = target;
        this.stateChangedAt = occurredAt;
        registerEvidence(occurredAt, now);
        if (target == ApplicationState.SUBMITTED && appliedAt == null) {
            this.appliedAt = occurredAt;
        }
    }

    void registerEvidence(Instant occurredAt, Instant now) {
        if (occurredAt.isAfter(lastEvidenceAt)) {
            this.lastEvidenceAt = occurredAt;
        }
        this.updatedAt = now;
    }

    void planNext(String action, Instant dueAt, Instant now) {
        if (action != null) {
            this.nextAction = action;
        }
        if (dueAt != null) {
            this.nextDueAt = dueAt;
        }
        this.updatedAt = now;
    }

    public boolean isStale(Instant now, Duration staleAfter) {
        return state.isActive() && lastEvidenceAt.isBefore(now.minus(staleAfter));
    }

    record Details(UUID jobId, UUID profileId, String platform, String company, String companyNorm, String title,
            String titleNorm, String jobUrl, String urlHash, String country, Instant appliedAt, String notes,
            String nextAction, Instant nextDueAt) {
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public Origin getOrigin() {
        return origin;
    }

    public String getPlatform() {
        return platform;
    }

    public String getCompany() {
        return company;
    }

    public String getTitle() {
        return title;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public String getCountry() {
        return country;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public ApplicationState getState() {
        return state;
    }

    public Instant getStateChangedAt() {
        return stateChangedAt;
    }

    public Instant getLastEvidenceAt() {
        return lastEvidenceAt;
    }

    public String getNextAction() {
        return nextAction;
    }

    public Instant getNextDueAt() {
        return nextDueAt;
    }

    public Integer getMatchScore() {
        return matchScore;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
