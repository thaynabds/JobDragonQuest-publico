package dev.jobdragonquest.profiles;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Competência declarada no perfil. Nível de 1 (noções) a 5 (domínio) e anos de uso opcionais. */
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

    protected ProfileSkill() {
    }

    public ProfileSkill(String name, String normalized, int level, BigDecimal years) {
        this.name = name;
        this.normalized = normalized;
        this.level = level;
        this.years = years;
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
}
