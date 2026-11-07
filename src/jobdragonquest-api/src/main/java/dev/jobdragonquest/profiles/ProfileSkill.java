package dev.jobdragonquest.profiles;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Competência declarada no perfil. Nível de 1 (noções) a 5 (domínio) e tempo de experiência em anos, meses e dias.
 * {@code years} guarda o total em anos (uma casa decimal), usado na análise de compatibilidade.
 */
@Embeddable
public class ProfileSkill {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 80)
    private String normalized;

    @Column(nullable = false)
    private int level;

    @Column(precision = 4, scale = 1)
    private BigDecimal years;

    @Column(name = "exp_years")
    private Integer expYears;

    @Column(name = "exp_months")
    private Integer expMonths;

    @Column(name = "exp_days")
    private Integer expDays;

    protected ProfileSkill() {
    }

    public ProfileSkill(String name, String normalized, int level, Integer expYears, Integer expMonths, Integer expDays) {
        this.name = name;
        this.normalized = normalized;
        this.level = level;
        this.expYears = expYears;
        this.expMonths = expMonths;
        this.expDays = expDays;
        this.years = totalYears(expYears, expMonths, expDays);
    }

    /** Total em anos, com uma casa decimal, ou null quando nenhum tempo foi informado. */
    public static BigDecimal totalYears(Integer years, Integer months, Integer days) {
        if (years == null && months == null && days == null) {
            return null;
        }
        double total = (years == null ? 0 : years) + (months == null ? 0 : months) / 12.0 + (days == null ? 0 : days) / 365.0;
        return BigDecimal.valueOf(total).setScale(1, java.math.RoundingMode.HALF_UP);
    }

    public String getName() {
        return name;
    }

    public String getNormalized() {
        return normalized;
    }

    public int getLevel() {
        return level;
    }

    public BigDecimal getYears() {
        return years;
    }

    public Integer getExpYears() {
        return expYears;
    }

    public Integer getExpMonths() {
        return expMonths;
    }

    public Integer getExpDays() {
        return expDays;
    }
}
