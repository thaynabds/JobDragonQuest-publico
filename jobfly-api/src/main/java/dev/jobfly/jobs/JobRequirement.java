package dev.jobfly.jobs;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Requisito da vaga. {@code skill} é a competência normalizada, quando o requisito se refere a uma.
 * Um requisito sem competência (ex.: "boa comunicação") é listado na análise como "não avaliado automaticamente".
 */
@Embeddable
public class JobRequirement {

    public enum Kind {
        MANDATORY,
        DESIRED
    }

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(length = 80)
    private String skill;

    @Column(name = "min_years", precision = 4, scale = 1)
    private BigDecimal minYears;

    protected JobRequirement() {
    }

    public JobRequirement(Kind kind, String text, String skill, BigDecimal minYears) {
        this.kind = kind;
        this.text = text;
        this.skill = skill;
        this.minYears = minYears;
    }

    public Kind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public String getSkill() {
        return skill;
    }

    public BigDecimal getMinYears() {
        return minYears;
    }
}
