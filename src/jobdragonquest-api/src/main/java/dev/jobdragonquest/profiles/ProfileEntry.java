package dev.jobdragonquest.profiles;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jobdragonquest.profiles.ProfileEnums.Degree;
import dev.jobdragonquest.profiles.ProfileEnums.EmploymentType;
import dev.jobdragonquest.profiles.ProfileEnums.EntryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Item do histórico do perfil: experiência, formação, projeto, curso, certificação, voluntariado, prêmio ou publicação.
 * Os campos extras valem conforme o tipo (grau e área para formação, credencial e validade para certificação...).
 */
@Embeddable
public class ProfileEntry {

    public enum Kind {
        EXPERIENCE,
        ACADEMIC_EXPERIENCE,
        EDUCATION,
        PROJECT,
        COURSE,
        CERTIFICATION,
        VOLUNTEER,
        AWARD,
        PUBLICATION
    }

    /** Campos opcionais de cada item, já validados pelo serviço. */
    public record Details(String location, EmploymentType employmentType, Boolean current, Degree degree,
            String fieldOfStudy, EntryStatus status, String credentialId, LocalDate expirationDate, Integer hours,
            String skills) {

        static final Details EMPTY = new Details(null, null, null, null, null, null, null, null, null, null);
    }

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(name = "show_in_resume", nullable = false)
    private boolean showInResume = true;

    @Column(name = "extra_urls", columnDefinition = "text")
    private String extraUrls;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 200)
    private String organization;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(length = 4000)
    private String description;

    @Column(length = 2048)
    private String url;

    @Column(length = 160)
    private String location;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "employment_type", length = 20)
    private EmploymentType employmentType;

    @Column(name = "is_current")
    private Boolean current;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private Degree degree;

    @Column(name = "field_of_study", length = 160)
    private String fieldOfStudy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private EntryStatus status;

    @Column(name = "credential_id", length = 120)
    private String credentialId;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    private Integer hours;

    @Column(length = 500)
    private String skills;

    protected ProfileEntry() {
    }

    public ProfileEntry(Kind kind, String title, String organization, LocalDate startDate, LocalDate endDate,
            String description, String url, Details details) {
        this.kind = kind;
        this.title = title;
        this.organization = organization;
        this.startDate = startDate;
        this.endDate = endDate;
        this.description = description;
        this.url = url;
        Details d = details == null ? Details.EMPTY : details;
        this.location = d.location();
        this.employmentType = d.employmentType();
        this.current = d.current();
        this.degree = d.degree();
        this.fieldOfStudy = d.fieldOfStudy();
        this.status = d.status();
        this.credentialId = d.credentialId();
        this.expirationDate = d.expirationDate();
        this.hours = d.hours();
        this.skills = d.skills();
    }

    public Kind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getOrganization() {
        return organization;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    ProfileEntry withResume(Boolean show, java.util.List<String> urls) {
        this.showInResume = !Boolean.FALSE.equals(show);
        this.extraUrls = urls == null || urls.isEmpty() ? null : String.join("\n", urls);
        return this;
    }

    public boolean isShowInResume() {
        return showInResume;
    }

    public java.util.List<String> getExtraUrls() {
        return extraUrls == null ? java.util.List.of() : java.util.List.of(extraUrls.split("\n"));
    }

    public Details getDetails() {
        return new Details(location, employmentType, current, degree, fieldOfStudy, status, credentialId, expirationDate,
                hours, skills);
    }
}
