package dev.jobdragonquest.applications;

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
        /** Envio que a pessoa confirmou, com um clique, na extensão. Conta para liberar o automático. */
        EXTENSION,
        /** Envio feito pelo automático da extensão, com a liga ativa. Não conta para liberar o automático. */
        EXTENSION_AUTOMATICO;

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
