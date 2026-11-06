package dev.jobfly.notifications;

/** Tipos de aviso. Cada tipo (ou etapa, no caso de STAGE_CHANGED) é um tópico configurável. */
public enum NotificationType {
    STAGE_CHANGED,
    ACTION_REQUIRED,
    TECH_TEST,
    INTERVIEW_REMINDER,
    DEADLINE_REMINDER,
    SUGGESTION_TO_REVIEW,
    SOURCE_FAILING,
    SOURCE_TO_CHECK,
    NO_UPDATE,
    NEW_JOBS,
    TEST
}
