package dev.jobfly.sources;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jobfly.common.persistence.AssignedIdEntity;
import dev.jobfly.sources.SourceKind.CollectionMode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Site, ATS ou rede social vigiado por vagas, ligado a um ou mais perfis do mesmo usuário. */
@Entity
@Table(name = "watched_source")
public class WatchedSource extends AssignedIdEntity {

    /** Teto do recuo após falhas seguidas: nunca espera mais de um dia para tentar de novo. */
    static final Duration MAX_BACKOFF = Duration.ofHours(24);

    public enum RunStatus {
        OK,
        FAILED,
        CHECKED
    }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private SourceKind kind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "collection_mode", nullable = false, length = 20)
    private CollectionMode collectionMode;

    @Column(length = 100)
    private String identifier;

    @Column(length = 2048)
    private String url;

    @Column(name = "dedup_key", nullable = false, length = 64)
    private String dedupKey;

    @Column(name = "frequency_minutes", nullable = false)
    private int frequencyMinutes;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "last_status", length = 20)
    private RunStatus lastStatus;

    @Column(name = "last_message", length = 500)
    private String lastMessage;

    @Column(name = "last_found")
    private Integer lastFound;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @ElementCollection
    @CollectionTable(name = "watched_source_profile", joinColumns = @JoinColumn(name = "source_id"))
    @Column(name = "profile_id", nullable = false)
    private Set<UUID> profileIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "watched_source_keyword", joinColumns = @JoinColumn(name = "source_id"))
    @OrderColumn(name = "position")
    @Column(name = "keyword", nullable = false, length = 80)
    private List<String> keywords = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected WatchedSource() {
    }

    WatchedSource(UUID userId, SourceKind kind, String dedupKey, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.kind = kind;
        this.collectionMode = kind.mode();
        this.dedupKey = dedupKey;
        this.enabled = true;
        // Primeira coleta ou lembrete logo após o cadastro.
        this.nextRunAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void configure(String name, String identifier, String url, int frequencyMinutes, List<String> keywords, Instant now) {
        this.name = name;
        this.identifier = identifier;
        this.url = url;
        this.frequencyMinutes = frequencyMinutes;
        this.keywords.clear();
        this.keywords.addAll(keywords);
        this.updatedAt = now;
    }

    void updateDedupKey(String key) {
        this.dedupKey = key;
    }

    void replaceProfiles(Set<UUID> ids, Instant now) {
        profileIds.clear();
        profileIds.addAll(ids);
        updatedAt = now;
    }

    void addProfiles(Set<UUID> ids, Instant now) {
        profileIds.addAll(ids);
        updatedAt = now;
    }

    void removeProfiles(Set<UUID> ids, Instant now) {
        profileIds.removeAll(ids);
        updatedAt = now;
    }

    void changeFrequency(int minutes, Instant now) {
        this.frequencyMinutes = minutes;
        if (enabled && consecutiveFailures == 0) {
            Instant candidate = (lastRunAt == null ? now : lastRunAt).plus(Duration.ofMinutes(minutes));
            nextRunAt = candidate.isBefore(now) ? now : candidate;
        }
        this.updatedAt = now;
    }

    void enable(boolean value, Instant now) {
        if (value && !enabled) {
            nextRunAt = now;
            consecutiveFailures = 0;
        }
        this.enabled = value;
        this.updatedAt = now;
    }

    void recordSuccess(RunStatus status, int found, String message, Instant now) {
        lastRunAt = now;
        lastStatus = status;
        lastFound = found;
        lastMessage = message;
        consecutiveFailures = 0;
        nextRunAt = now.plus(Duration.ofMinutes(frequencyMinutes));
        updatedAt = now;
    }

    /** Recuo exponencial: frequência × 2^(falhas−1), limitado a {@link #MAX_BACKOFF}. */
    void recordFailure(String message, Instant now) {
        lastRunAt = now;
        lastStatus = RunStatus.FAILED;
        lastMessage = message;
        consecutiveFailures++;
        long factor = 1L << Math.min(consecutiveFailures - 1, 10);
        Duration wait = Duration.ofMinutes(frequencyMinutes).multipliedBy(factor);
        nextRunAt = now.plus(wait.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : wait);
        updatedAt = now;
    }

    public boolean isDue(Instant now) {
        return enabled && !nextRunAt.isAfter(now);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public SourceKind getKind() {
        return kind;
    }

    public CollectionMode getCollectionMode() {
        return collectionMode;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getUrl() {
        return url;
    }

    public int getFrequencyMinutes() {
        return frequencyMinutes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public RunStatus getLastStatus() {
        return lastStatus;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public Integer getLastFound() {
        return lastFound;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public Set<UUID> getProfileIds() {
        return Set.copyOf(profileIds);
    }

    public List<String> getKeywords() {
        return List.copyOf(keywords);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
