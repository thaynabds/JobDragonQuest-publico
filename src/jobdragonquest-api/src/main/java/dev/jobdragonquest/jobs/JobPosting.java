package dev.jobdragonquest.jobs;

import dev.jobdragonquest.common.persistence.AssignedIdEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jobdragonquest.common.domain.Seniority;
import dev.jobdragonquest.common.domain.WorkMode;
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

/** Vaga encontrada ou registrada pelo usuário. Guarda a origem e a URL original (RF-03.1). */
@Entity
@Table(name = "job")
public class JobPosting extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "canonical_url", length = 2048)
    private String canonicalUrl;

    @Column(name = "url_hash", length = 64)
    private String urlHash;

    @Column(nullable = false, length = 200)
    private String company;

    @Column(name = "company_norm", nullable = false, length = 200)
    private String companyNorm;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "title_norm", nullable = false, length = 200)
    private String titleNorm;

    @Column(length = 200)
    private String location;

    @Column(length = 2)
    private String country;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "work_mode", length = 20)
    private WorkMode workMode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private Seniority seniority;

    @Column(length = 20000)
    private String description;

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "salary_currency", length = 3)
    private String salaryCurrency;

    @Column(name = "salary_period", length = 10)
    private String salaryPeriod;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @ElementCollection
    @CollectionTable(name = "job_requirement", joinColumns = @JoinColumn(name = "job_id"))
    @OrderColumn(name = "position")
    private List<JobRequirement> requirements = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "job_language", joinColumns = @JoinColumn(name = "job_id"))
    @OrderColumn(name = "position")
    private List<JobLanguage> languages = new ArrayList<>();

    @Column(name = "found_at", nullable = false)
    private Instant foundAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Marcada pela pessoa como favorita: base da busca por vagas parecidas. */
    @Column(nullable = false)
    private boolean favorite;

    /** Fonte vigiada que encontrou a vaga; null quando ela foi cadastrada à mão. */
    @Column(name = "watched_source_id")
    private UUID watchedSourceId;

    protected JobPosting() {
    }

    JobPosting(UUID userId, JobData data, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.foundAt = now;
        this.status = JobStatus.OPEN;
        apply(data, now);
    }

    void apply(JobData data, Instant now) {
        this.source = data.source();
        this.sourceUrl = data.sourceUrl();
        this.canonicalUrl = data.canonicalUrl();
        this.urlHash = data.urlHash();
        this.company = data.company();
        this.companyNorm = data.companyNorm();
        this.title = data.title();
        this.titleNorm = data.titleNorm();
        this.location = data.location();
        this.country = data.country();
        this.workMode = data.workMode();
        this.seniority = data.seniority();
        this.description = data.description();
        this.salaryMin = data.salaryMin();
        this.salaryMax = data.salaryMax();
        this.salaryCurrency = data.salaryCurrency();
        this.salaryPeriod = data.salaryPeriod();
        this.requirements.clear();
        this.requirements.addAll(data.requirements());
        this.languages.clear();
        this.languages.addAll(data.languages());
        this.updatedAt = now;
    }

    void linkSource(UUID sourceId) {
        this.watchedSourceId = sourceId;
    }

    void changeStatus(JobStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    record JobData(String source, String sourceUrl, String canonicalUrl, String urlHash, String company,
            String companyNorm, String title, String titleNorm, String location, String country, WorkMode workMode,
            Seniority seniority, String description, BigDecimal salaryMin, BigDecimal salaryMax, String salaryCurrency,
            String salaryPeriod, List<JobRequirement> requirements, List<JobLanguage> languages) {
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getSource() {
        return source;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public String getCompany() {
        return company;
    }

    public String getTitle() {
        return title;
    }

    public String getLocation() {
        return location;
    }

    public String getCountry() {
        return country;
    }

    public WorkMode getWorkMode() {
        return workMode;
    }

    public Seniority getSeniority() {
        return seniority;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getSalaryMin() {
        return salaryMin;
    }

    public BigDecimal getSalaryMax() {
        return salaryMax;
    }

    public String getSalaryCurrency() {
        return salaryCurrency;
    }

    public String getSalaryPeriod() {
        return salaryPeriod;
    }

    public JobStatus getStatus() {
        return status;
    }

    public List<JobRequirement> getRequirements() {
        return List.copyOf(requirements);
    }

    public List<JobLanguage> getLanguages() {
        return List.copyOf(languages);
    }

    public Instant getFoundAt() {
        return foundAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isFavorite() {
        return favorite;
    }

    void markFavorite(boolean value) {
        this.favorite = value;
    }

    public UUID getWatchedSourceId() {
        return watchedSourceId;
    }
}
