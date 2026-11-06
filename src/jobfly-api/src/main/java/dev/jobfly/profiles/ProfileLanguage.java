package dev.jobfly.profiles;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jobfly.common.domain.LanguageLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class ProfileLanguage {

    /** Código ISO 639-1 em minúsculas (pt, en, es...). */
    @Column(nullable = false, length = 8)
    private String code;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private LanguageLevel level;

    protected ProfileLanguage() {
    }

    public ProfileLanguage(String code, LanguageLevel level) {
        this.code = code;
        this.level = level;
    }

    public String getCode() {
        return code;
    }

    public LanguageLevel getLevel() {
        return level;
    }
}
