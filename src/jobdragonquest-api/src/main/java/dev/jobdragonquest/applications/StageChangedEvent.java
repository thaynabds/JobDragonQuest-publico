package dev.jobdragonquest.applications;

import java.util.UUID;

import dev.jobdragonquest.applications.ApplicationEnums.EventSource;

/**
 * Publicado quando a etapa de uma candidatura muda de fato (inclusive no cadastro). Os avisos escutam este evento
 * depois do commit, então uma falha ao criar o aviso nunca desfaz a mudança de etapa.
 */
public record StageChangedEvent(UUID userId, UUID applicationId, UUID eventId, ApplicationState fromState,
        ApplicationState toState, EventSource source, String company, String title) {
}
