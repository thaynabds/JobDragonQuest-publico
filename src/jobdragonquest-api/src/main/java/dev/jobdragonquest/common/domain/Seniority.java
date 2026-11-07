package dev.jobdragonquest.common.domain;

/** Senioridade em ordem crescente. A ordem é usada na análise de compatibilidade. */
public enum Seniority {
    INTERN,
    JUNIOR,
    MID,
    SENIOR,
    LEAD;

    public int gapTo(Seniority required) {
        return required.ordinal() - ordinal();
    }
}
