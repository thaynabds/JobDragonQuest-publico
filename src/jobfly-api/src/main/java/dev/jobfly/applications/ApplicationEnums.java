package dev.jobfly.applications;

public final class ApplicationEnums {

    private ApplicationEnums() {
    }

    /** Como a candidatura entrou no sistema (seção 23 do prompt). */
    public enum Origin {
        AUTOMATION,
        MANUAL,
        LINK,
        EMAIL,
        BROWSER_EXTENSION,
        IMPORT,
        SOCIAL,
        REFERRAL
    }

    /** De onde veio a informação que motivou um evento. */
    public enum EventSource {
        MANUAL,
        EMAIL,
        PORTAL,
        AGENT,
        SYSTEM,
        EXTENSION;

        public boolean isHuman() {
            return this == MANUAL;
        }
    }

    public enum EventKind {
        STATE_CHANGE,
        NOTE,
        /** Mudança proposta com confiança baixa: só vale depois que a pessoa aceita (RN-03). */
        SUGGESTION
    }

    public enum EventStatus {
        APPLIED,
        PENDING,
        ACCEPTED,
        DISMISSED
    }
}
