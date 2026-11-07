package dev.jobdragonquest.profiles;

import dev.jobdragonquest.common.persistence.AssignedIdEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jobdragonquest.common.domain.Seniority;
import dev.jobdragonquest.common.domain.WorkMode;
import dev.jobdragonquest.profiles.ProfileEnums.EmploymentType;
import dev.jobdragonquest.profiles.ProfileEnums.SalaryPeriod;
import dev.jobdragonquest.profiles.ProfileEnums.TravelAvailability;
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

    @Column(length = 220)
    private String headline;

    @Column(length = 4000)
    private String summary;

    @Column(name = "current_title", length = 160)
    private String currentTitle;

    @Column(length = 120)
    private String city;

    @Column(name = "state_region", length = 120)
    private String stateRegion;

    @Column(length = 2)
    private String country;

    @Column(name = "requires_sponsorship")
    private Boolean requiresSponsorship;

    @Column(name = "willing_to_relocate")
    private Boolean willingToRelocate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "travel_availability", length = 20)
    private TravelAvailability travelAvailability;

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "current_salary", precision = 12, scale = 2)
    private BigDecimal currentSalary;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "salary_period", length = 10)
    private SalaryPeriod salaryPeriod;

    @Column(name = "linkedin_url", length = 2048)
    private String linkedinUrl;

    @Column(name = "github_url", length = 2048)
    private String githubUrl;

    @Column(name = "portfolio_url", length = 2048)
    private String portfolioUrl;

    @Column(name = "website_url", length = 2048)
    private String websiteUrl;

    @Column(name = "cover_letter", length = 8000)
    private String coverLetter;

    /** Opcional e sensível (LGPD): só é guardado se a pessoa quiser informar. */
    private Boolean disability;

    @Column(name = "disability_details", length = 500)
    private String disabilityDetails;

    @ElementCollection
    @CollectionTable(name = "profile_work_country", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderColumn(name = "position")
    @Column(name = "country", nullable = false, length = 2)
    private List<String> workAuthorization = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "profile_employment_type", joinColumns = @JoinColumn(name = "profile_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "employment_type", nullable = false, length = 20)
    private Set<EmploymentType> employmentTypes = EnumSet.noneOf(EmploymentType.class);

    @ElementCollection
    @CollectionTable(name = "profile_excluded_company", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderColumn(name = "position")
    @Column(name = "company", nullable = false, length = 160)
    private List<String> excludedCompanies = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "profile_include_keyword", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderColumn(name = "position")
    @Column(name = "keyword", nullable = false, length = 80)
    private List<String> includeKeywords = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "profile_exclude_keyword", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderColumn(name = "position")
    @Column(name = "keyword", nullable = false, length = 80)
    private List<String> excludeKeywords = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "profile_industry", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderColumn(name = "position")
    @Column(name = "industry", nullable = false, length = 120)
    private List<String> industries = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "profile_answer", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderColumn(name = "position")
    private List<ProfileAnswer> answers = new ArrayList<>();

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
        ProfileDetails d = data.details();
        this.headline = d.headline();
        this.summary = d.summary();
        this.currentTitle = d.currentTitle();
        this.city = d.city();
        this.stateRegion = d.stateRegion();
        this.country = d.country();
        this.requiresSponsorship = d.requiresSponsorship();
        this.willingToRelocate = d.willingToRelocate();
        this.travelAvailability = d.travelAvailability();
        this.noticePeriodDays = d.noticePeriodDays();
        this.availableFrom = d.availableFrom();
        this.currentSalary = d.currentSalary();
        this.salaryPeriod = d.salaryPeriod();
        this.linkedinUrl = d.linkedinUrl();
        this.githubUrl = d.githubUrl();
        this.portfolioUrl = d.portfolioUrl();
        this.websiteUrl = d.websiteUrl();
        this.coverLetter = d.coverLetter();
        this.disability = d.disability();
        this.disabilityDetails = Boolean.TRUE.equals(d.disability()) ? d.disabilityDetails() : null;
        replace(workAuthorization, d.workAuthorization());
        employmentTypes.clear();
        employmentTypes.addAll(d.employmentTypes());
        replace(excludedCompanies, d.excludedCompanies());
        replace(includeKeywords, d.includeKeywords());
        replace(excludeKeywords, d.excludeKeywords());
        replace(industries, d.industries());
        replace(answers, d.answers());
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
            List<ProfileSkill> skills, List<ProfileLanguage> languages, List<ProfileEntry> entries,
            ProfileDetails details) {
    }

    /** Campos do perfil completo (identificação, localização, contratação, links, filtros e respostas). */
    record ProfileDetails(String headline, String summary, String currentTitle, String city, String stateRegion,
            String country, Boolean requiresSponsorship, Boolean willingToRelocate, TravelAvailability travelAvailability,
            Integer noticePeriodDays, LocalDate availableFrom, BigDecimal currentSalary, SalaryPeriod salaryPeriod,
            String linkedinUrl, String githubUrl, String portfolioUrl, String websiteUrl, String coverLetter,
            Boolean disability, String disabilityDetails, List<String> workAuthorization,
            Set<EmploymentType> employmentTypes, List<String> excludedCompanies, List<String> includeKeywords,
            List<String> excludeKeywords, List<String> industries, List<ProfileAnswer> answers) {
    }

    ProfileDetails getDetails() {
        return new ProfileDetails(headline, summary, currentTitle, city, stateRegion, country, requiresSponsorship,
                willingToRelocate, travelAvailability, noticePeriodDays, availableFrom, currentSalary, salaryPeriod,
                linkedinUrl, githubUrl, portfolioUrl, websiteUrl, coverLetter, disability, disabilityDetails,
                List.copyOf(workAuthorization), Set.copyOf(employmentTypes), List.copyOf(excludedCompanies),
                List.copyOf(includeKeywords), List.copyOf(excludeKeywords), List.copyOf(industries), List.copyOf(answers));
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
