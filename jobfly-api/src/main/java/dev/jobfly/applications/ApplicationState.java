package dev.jobfly.applications;

/**
 * Etapas da candidatura (seção 9 do prompt). "Sem atualização" não é um estado: é o indicador {@code stale}, calculado
 * a partir da última evidência, porque o silêncio não muda a etapa (RN-05/RF-05.4).
 */
public enum ApplicationState {
    JOB_FOUND(Phase.PREPARATION),
    JOB_ANALYZED(Phase.PREPARATION),
    RECOMMENDED(Phase.PREPARATION),
    PREPARED(Phase.PREPARATION),
    AWAITING_USER(Phase.PREPARATION),
    SUBMITTED(Phase.INTAKE),
    CONFIRMED(Phase.INTAKE),
    SCREENING(Phase.PROCESS),
    RECRUITER_CONTACT(Phase.PROCESS),
    INTERVIEW_SCHEDULED(Phase.PROCESS),
    INTERVIEW_DONE(Phase.PROCESS),
    TECH_TEST_PENDING(Phase.PROCESS),
    TECH_TEST_DONE(Phase.PROCESS),
    BEHAVIORAL_ASSESSMENT(Phase.PROCESS),
    ADDITIONAL_STAGE(Phase.PROCESS),
    OFFER_RECEIVED(Phase.OFFER),
    APPROVED(Phase.FINAL),
    REJECTED(Phase.FINAL),
    CLOSED(Phase.FINAL);

    public enum Phase {
        PREPARATION,
        INTAKE,
        PROCESS,
        OFFER,
        FINAL
    }

    private final Phase phase;

    ApplicationState(Phase phase) {
        this.phase = phase;
    }

    public Phase phase() {
        return phase;
    }

    public boolean isFinal() {
        return phase == Phase.FINAL;
    }

    /** Candidatura enviada e ainda em andamento: é a que pode ficar "sem atualização". */
    public boolean isActive() {
        return phase == Phase.INTAKE || phase == Phase.PROCESS || phase == Phase.OFFER;
    }
}
