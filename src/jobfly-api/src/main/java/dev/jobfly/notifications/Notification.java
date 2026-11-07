package dev.jobfly.notifications;

import java.time.Instant;
import java.util.UUID;

import dev.jobfly.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Aviso já gerado. Os canais (pop-up, sistema, som) são decididos na criação, pelas preferências e pelo horário de
 * silêncio daquele momento. {@code dedupeKey} é único por pessoa: o mesmo fato nunca vira dois avisos.
 */
@Entity
@Table(name = "notification")
public class Notification extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationPriority priority;

    @Column(name = "dedupe_key", nullable = false)
    private String dedupeKey;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "source_id")
    private UUID sourceId;

    private String state;
    private String company;
    private String title;
    private String detail;

    @Column(nullable = false)
    private String message;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "item_count")
    private Integer itemCount;

    @Column(name = "lead_minutes")
    private Integer leadMinutes;

    @Column(nullable = false)
    private boolean popup;

    @Column(name = "system_alert", nullable = false)
    private boolean systemAlert;

    @Column(nullable = false)
    private boolean sound;

    @Column(nullable = false)
    private boolean quiet;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "snoozed_until")
    private Instant snoozedUntil;

    protected Notification() {
    }

    Notification(UUID userId, NotificationDraft draft, boolean popup, boolean systemAlert, boolean sound, boolean quiet,
            Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.topic = draft.topic();
        this.type = draft.type();
        this.priority = draft.priority();
        this.dedupeKey = draft.dedupeKey();
        this.applicationId = draft.applicationId();
        this.sourceId = draft.sourceId();
        this.state = draft.state() == null ? null : draft.state().name();
        this.company = cut(draft.company(), 200);
        this.title = cut(draft.title(), 300);
        this.detail = cut(draft.detail(), 500);
        this.message = cut(draft.message(), 700);
        this.dueAt = draft.dueAt();
        this.itemCount = draft.itemCount();
        this.leadMinutes = draft.leadMinutes();
        this.popup = popup;
        this.systemAlert = systemAlert;
        this.sound = sound;
        this.quiet = quiet;
        this.createdAt = now;
        // Sem nenhum canal visível, não há o que entregar: o aviso fica só no sino.
        this.deliveredAt = popup || systemAlert || sound ? null : now;
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    void markDelivered(Instant now) {
        if (deliveredAt == null) {
            deliveredAt = now;
        }
    }

    void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
        markDelivered(now);
    }

    /** Adiar devolve o aviso para a fila de entrega depois do prazo, como não lido. */
    void snooze(Instant until) {
        snoozedUntil = until;
        readAt = null;
        deliveredAt = null;
        popup = true;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTopic() {
        return topic;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public String getState() {
        return state;
    }

    public String getCompany() {
        return company;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public String getMessage() {
        return message;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public Integer getLeadMinutes() {
        return leadMinutes;
    }

    public boolean isPopup() {
        return popup;
    }

    public boolean isSystemAlert() {
        return systemAlert;
    }

    public boolean isSound() {
        return sound;
    }

    public boolean isQuiet() {
        return quiet;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getSnoozedUntil() {
        return snoozedUntil;
    }
}
