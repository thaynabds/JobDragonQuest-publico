package dev.jobdragonquest.profiles;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Item do histórico do perfil: experiência, formação, projeto, curso ou certificação. */
@Embeddable
public class ProfileEntry {

    public enum Kind {
        EXPERIENCE,
        ACADEMIC_EXPERIENCE,
        EDUCATION,
        PROJECT,
        COURSE,
        CERTIFICATION
    }

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Kind kind;

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

    protected ProfileEntry() {
    }

    public ProfileEntry(Kind kind, String title, String organization, LocalDate startDate, LocalDate endDate,
            String description, String url) {
        this.kind = kind;
        this.title = title;
        this.organization = organization;
        this.startDate = startDate;
        this.endDate = endDate;
        this.description = description;
        this.url = url;
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
}
