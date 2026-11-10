package dev.jobdragonquest.notifications;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
            boolean quiet, Instant createdAt, Instant deliveredAt, Instant readAt, Instant snoozedUntil, String actionUrl,
            String body) {

        static NotificationResponse of(Notification n) {
            return new NotificationResponse(n.getId(), n.getTopic(), n.getType(), n.getPriority(), n.getApplicationId(),
                    n.getSourceId(), n.getState(), n.getCompany(), n.getTitle(), n.getDetail(), n.getMessage(), n.getDueAt(),
                    n.getItemCount(), n.getLeadMinutes(), n.isPopup(), n.isSystemAlert(), n.isSound(), n.isQuiet(),
                    n.getCreatedAt(), n.getDeliveredAt(), n.getReadAt(), n.getSnoozedUntil(), n.getActionUrl(), n.getBody());
        }
    }

    /** Aviso criado pela própria pessoa, com onde agir e o texto completo (ex.: e-mail de um processo seletivo). */
    public record ManualRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 200) String company,
            @NotBlank @Size(max = 700) String message,
            @Size(max = 2048) String actionUrl,
            @Size(max = 20000) String body) {
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
