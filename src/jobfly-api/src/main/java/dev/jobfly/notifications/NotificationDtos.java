package dev.jobfly.notifications;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    /**
     * Aviso para o painel e o MCP. {@code message} é a frase pronta em português; os campos estruturados deixam o
     * cliente montar a frase em outro idioma. {@code detail} e os nomes podem vir de fontes externas: exiba como texto.
     */
    public record NotificationResponse(UUID id, String topic, NotificationType type, NotificationPriority priority,
            UUID applicationId, UUID sourceId, String state, String company, String title, String detail, String message,
            Instant dueAt, Integer itemCount, Integer leadMinutes, boolean popup, boolean system, boolean sound,
            boolean quiet, Instant createdAt, Instant deliveredAt, Instant readAt, Instant snoozedUntil) {

        static NotificationResponse of(Notification n) {
            return new NotificationResponse(n.getId(), n.getTopic(), n.getType(), n.getPriority(), n.getApplicationId(),
                    n.getSourceId(), n.getState(), n.getCompany(), n.getTitle(), n.getDetail(), n.getMessage(), n.getDueAt(),
                    n.getItemCount(), n.getLeadMinutes(), n.isPopup(), n.isSystemAlert(), n.isSound(), n.isQuiet(),
                    n.getCreatedAt(), n.getDeliveredAt(), n.getReadAt(), n.getSnoozedUntil());
        }
    }

    public record NotificationList(List<NotificationResponse> items, long unreadCount, int page, int size,
            long totalItems) {
    }

    public record IdsRequest(List<UUID> ids) {
    }

    public record SnoozeRequest(int minutes) {
    }

    public record CountResponse(int count) {
    }
}
