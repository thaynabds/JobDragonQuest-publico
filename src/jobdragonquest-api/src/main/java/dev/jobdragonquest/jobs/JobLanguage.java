package dev.jobdragonquest.jobs;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jobdragonquest.common.domain.LanguageLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class JobLanguage {

    @Column(nullable = false, length = 8)
    private String code;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "min_level", nullable = false, length = 10)
    private LanguageLevel minLevel;

    /** true = exigido (eliminatório); false = desejável. */
    @Column(nullable = false)
    private boolean required;

    protected JobLanguage() {
    }

    public JobLanguage(String code, LanguageLevel minLevel, boolean required) {
        this.code = code;
        this.minLevel = minLevel;
        this.required = required;
    }

    public String getCode() {
        return code;
    }

    public LanguageLevel getMinLevel() {
        return minLevel;
    }

    public boolean isRequired() {
        return required;
    }
}
