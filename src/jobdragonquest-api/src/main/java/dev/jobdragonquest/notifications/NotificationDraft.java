package dev.jobdragonquest.notifications;

import java.time.Instant;
import java.util.UUID;

import dev.jobdragonquest.applications.ApplicationState;

/** Fato que pode virar aviso. O serviço decide, pelas preferências, se e como ele aparece. */
public record NotificationDraft(String topic, NotificationType type, NotificationPriority priority, String dedupeKey,
        UUID applicationId, UUID sourceId, ApplicationState state, String company, String title, String detail,
        String message, Instant dueAt, Integer itemCount, Integer leadMinutes) {
}
