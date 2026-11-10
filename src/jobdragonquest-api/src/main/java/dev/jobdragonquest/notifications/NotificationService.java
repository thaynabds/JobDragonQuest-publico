package dev.jobdragonquest.notifications;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.error.NotFoundException;
import dev.jobdragonquest.notifications.NotificationDtos.NotificationList;
import dev.jobdragonquest.notifications.NotificationDtos.NotificationResponse;
import dev.jobdragonquest.notifications.NotificationPreferences.Preference;
import dev.jobdragonquest.notifications.NotificationPreferences.Settings;

/** Cria, lista e atualiza avisos. Toda consulta filtra pelo dono: uma pessoa nunca vê nem altera aviso de outra. */
@Service
public class NotificationService {

    static final int MAX_PAGE_SIZE = 50;
    static final int MAX_DUE = 20;
    static final int MAX_IDS = 100;
    static final int MIN_SNOOZE_MINUTES = 5;
    static final int MAX_SNOOZE_MINUTES = 1440;

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final NotificationPreferences preferences;
    private final Clock clock;
    private final TransactionTemplate independent;

    NotificationService(NotificationRepository notifications, NotificationPreferences preferences, Clock clock,
            PlatformTransactionManager txManager) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.clock = clock;
        this.independent = new TransactionTemplate(txManager);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Grava o aviso se o tópico estiver ligado e o fato ainda não tiver virado aviso. Roda em transação própria: uma
     * corrida na chave única (dois pedidos ao mesmo tempo) só descarta o repetido, sem afetar quem chamou.
     */
    public Optional<NotificationResponse> record(UUID userId, NotificationDraft draft) {
        try {
            return Optional.ofNullable(independent.execute(status -> {
                if (notifications.existsByUserIdAndDedupeKey(userId, draft.dedupeKey())) {
                    return null;
                }
                Preference preference = preferences.preference(userId, draft.topic());
                if (!preference.enabled()) {
                    return null;
                }
                Settings settings = preferences.settings(userId);
                boolean quiet = preferences.isQuiet(settings, preferences.zone(userId))
                        && !(draft.priority() == NotificationPriority.HIGH && settings.quietAllowHigh());
                Notification saved = notifications.saveAndFlush(new Notification(userId, draft,
                        preference.popup() && !quiet, preference.system() && !quiet, preference.sound() && !quiet,
                        quiet, clock.instant()));
                return NotificationResponse.of(saved);
            }));
        } catch (DataIntegrityViolationException ex) {
            log.debug("Aviso repetido descartado: {}", draft.dedupeKey());
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public NotificationList list(UUID userId, boolean unreadOnly, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw DomainException.badRequest("invalid_page", "Página inválida (tamanho de 1 a " + MAX_PAGE_SIZE + ")");
        }
        PageRequest request = PageRequest.of(page, size);
        Page<Notification> result = unreadOnly
                ? notifications.findByUserIdAndReadAtIsNullAndDismissedAtIsNullOrderByCreatedAtDesc(userId, request)
                : notifications.findByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(userId, request);
        return new NotificationList(result.map(NotificationResponse::of).getContent(),
                notifications.countByUserIdAndReadAtIsNull(userId), page, size, result.getTotalElements());
    }

    /** Avisos esperando entrega (pop-up, sistema ou som), inclusive os adiados cujo prazo passou. */
    @Transactional(readOnly = true)
    public List<NotificationResponse> due(UUID userId) {
        return notifications.findDue(userId, clock.instant(), PageRequest.of(0, MAX_DUE)).stream()
                .map(NotificationResponse::of).toList();
    }

    @Transactional
    public int markDelivered(UUID userId, List<UUID> ids) {
        List<UUID> valid = validIds(ids);
        Instant now = clock.instant();
        List<Notification> owned = notifications.findByUserIdAndIdIn(userId, valid);
        owned.forEach(notification -> notification.markDelivered(now));
        return owned.size();
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID id) {
        Notification notification = load(userId, id);
        notification.markRead(clock.instant());
        return NotificationResponse.of(notification);
    }

    @Transactional
    public void dismiss(UUID userId, UUID id) {
        notifications.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Aviso")).dismiss(clock.instant());
    }

    @Transactional
    public int dismissAll(UUID userId) {
        return notifications.dismissAll(userId, clock.instant());
    }

    @Transactional
    public int markAllRead(UUID userId) {
        Instant now = clock.instant();
        notifications.markAllDelivered(userId, now);
        return notifications.markAllRead(userId, now);
    }

    @Transactional
    public NotificationResponse snooze(UUID userId, UUID id, int minutes) {
        if (minutes < MIN_SNOOZE_MINUTES || minutes > MAX_SNOOZE_MINUTES) {
            throw DomainException.badRequest("invalid_snooze",
                    "Adie entre " + MIN_SNOOZE_MINUTES + " e " + MAX_SNOOZE_MINUTES + " minutos");
        }
        Notification notification = load(userId, id);
        notification.snooze(clock.instant().plus(Duration.ofMinutes(minutes)));
        return NotificationResponse.of(notification);
    }

    /** Aviso de teste, para a pessoa conferir pop-up, som e a permissão do navegador. Ignora o horário de silêncio. */
    public NotificationResponse test(UUID userId) {
        Instant now = clock.instant();
        NotificationDraft draft = new NotificationDraft(NotificationCatalog.TEST_TOPIC, NotificationType.TEST,
                NotificationPriority.HIGH, "test:" + now.toEpochMilli() + ":" + UUID.randomUUID(), null, null, null, null,
                null, null, "Aviso de teste: as notificações do JobDragonQuest estão funcionando.", null, null, null);
        return independent.execute(status -> NotificationResponse.of(
                notifications.saveAndFlush(new Notification(userId, draft, true, true, true, false, now))));
    }

    /** Cria um aviso da própria pessoa com onde agir e o texto completo. O endereço aceita só http e https. */
    public NotificationResponse createManual(UUID userId, NotificationDtos.ManualRequest request) {
        String url = request.actionUrl() == null || request.actionUrl().isBlank() ? null : request.actionUrl().strip();
        if (url != null && !isWebAddress(url)) {
            throw DomainException.badRequest("invalid_action_url", "O endereço para agir precisa começar com http:// ou https://");
        }
        Instant now = clock.instant();
        String topic = NotificationType.ACTION_REQUIRED.name();
        NotificationDraft draft = new NotificationDraft(topic, NotificationType.ACTION_REQUIRED, NotificationPriority.HIGH,
                "manual:" + now.toEpochMilli() + ":" + UUID.randomUUID(), null, null, null, request.company(),
                request.title().strip(), null, request.message().strip(), null, null, null);
        return independent.execute(status -> {
            Notification n = new Notification(userId, draft, true, true, true, false, now);
            n.withAction(url, request.body() == null || request.body().isBlank() ? null : request.body().strip());
            return NotificationResponse.of(notifications.saveAndFlush(n));
        });
    }

    private static boolean isWebAddress(String value) {
        try {
            java.net.URI uri = new java.net.URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            return (scheme.equals("https") || scheme.equals("http")) && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (java.net.URISyntaxException ex) {
            return false;
        }
    }

    @Transactional
    public int purgeOld(UUID userId, Duration retention) {
        return notifications.deleteReadBefore(userId, clock.instant().minus(retention));
    }

    private Notification load(UUID userId, UUID id) {
        return notifications.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Aviso"));
    }

    private static List<UUID> validIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > MAX_IDS || ids.stream().anyMatch(java.util.Objects::isNull)) {
            throw DomainException.badRequest("invalid_ids", "Envie de 1 a " + MAX_IDS + " ids");
        }
        return ids.stream().distinct().toList();
    }
}
