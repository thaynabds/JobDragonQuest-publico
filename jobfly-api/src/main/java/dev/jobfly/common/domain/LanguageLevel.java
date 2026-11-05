package dev.jobfly.common.domain;

/** Níveis do Quadro Europeu Comum de Referência (CEFR), mais nativo, em ordem crescente. */
public enum LanguageLevel {
    A1,
    A2,
    B1,
    B2,
    C1,
    C2,
    NATIVE;

    public boolean satisfies(LanguageLevel required) {
        return ordinal() >= required.ordinal();
    }
}
