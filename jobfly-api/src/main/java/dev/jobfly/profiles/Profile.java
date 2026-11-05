package dev.jobfly.profiles;

import dev.jobfly.common.persistence.AssignedIdEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jobfly.common.domain.Seniority;
import dev.jobfly.common.domain.WorkMode;
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

/**
 * Perfil profissional. Cada perfil é independente: competências, histórico e preferências não são compartilhados
 * entre perfis (RF-02.4).
 */
@Entity
@Table(name = "profile")
public class Profile extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 4000)
    private String objective;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Seniority seniority;

    @Column(name = "min_match_score", nullable = false)
    private int minMatchScore;

    @Column(name = "salary_expectation", precision = 12, scale = 2)
    private BigDecimal salaryExpectation;

    @Column(name = "salary_currency", length = 3)
    private String salaryCurrency;

    @Column(length = 500)
    private String availability;

    @Column(length = 2000)
    private String restrictions;

    @Column(name = "preferred_language", nullable = false, length = 8)
    private String preferredLanguage;

    @ElementCollection
    @CollectionTable(name = "profile_title", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderColumn(name = "position")
    @Column(name = "title", nullable = false, length = 160)
    private List<String> desiredTitles = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "profile_location", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderColumn(name = "position")
    @Column(name = "location", nullable = false, length = 160)
    private List<String> locations = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "profile_work_mode", joinColumns = @JoinColumn(name = "profile_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "work_mode", nullable = false, length = 20)
    private Set<WorkMode> workModes = EnumSet.noneOf(WorkMode.class);

    @ElementCollection
    @CollectionTable(name = "profile_skill", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderColumn(name = "position")
    private List<ProfileSkill> skills = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "profile_language", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderColumn(name = "position")
    private List<ProfileLanguage> languages = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "profile_entry", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderColumn(name = "position")
    private List<ProfileEntry> entries = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Profile() {
    }

    Profile(UUID userId, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(ProfileData data, Instant now) {
        this.name = data.name();
        this.objective = data.objective();
        this.seniority = data.seniority();
        this.minMatchScore = data.minMatchScore();
        this.salaryExpectation = data.salaryExpectation();
        this.salaryCurrency = data.salaryCurrency();
        this.availability = data.availability();
        this.restrictions = data.restrictions();
        this.preferredLanguage = data.preferredLanguage();
        replace(desiredTitles, data.desiredTitles());
        replace(locations, data.locations());
        workModes.clear();
        workModes.addAll(data.workModes());
        replace(skills, data.skills());
        replace(languages, data.languages());
        replace(entries, data.entries());
        this.updatedAt = now;
    }

    private static <T> void replace(List<T> target, List<T> values) {
        target.clear();
        target.addAll(values);
    }

    /** Dados já validados e normalizados pelo serviço. */
    record ProfileData(String name, String objective, Seniority seniority, int minMatchScore,
            BigDecimal salaryExpectation, String salaryCurrency, String availability, String restrictions,
            String preferredLanguage, List<String> desiredTitles, List<String> locations, Set<WorkMode> workModes,
            List<ProfileSkill> skills, List<ProfileLanguage> languages, List<ProfileEntry> entries) {
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getObjective() {
        return objective;
    }

    public Seniority getSeniority() {
        return seniority;
    }

    public int getMinMatchScore() {
        return minMatchScore;
    }

    public BigDecimal getSalaryExpectation() {
        return salaryExpectation;
    }

    public String getSalaryCurrency() {
        return salaryCurrency;
    }

    public String getAvailability() {
        return availability;
    }

    public String getRestrictions() {
        return restrictions;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public List<String> getDesiredTitles() {
        return List.copyOf(desiredTitles);
    }

    public List<String> getLocations() {
        return List.copyOf(locations);
    }

    public Set<WorkMode> getWorkModes() {
        return workModes.isEmpty() ? Set.of() : EnumSet.copyOf(workModes);
    }

    public List<ProfileSkill> getSkills() {
        return List.copyOf(skills);
    }

    public List<ProfileLanguage> getLanguages() {
        return List.copyOf(languages);
    }

    public List<ProfileEntry> getEntries() {
        return List.copyOf(entries);
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
