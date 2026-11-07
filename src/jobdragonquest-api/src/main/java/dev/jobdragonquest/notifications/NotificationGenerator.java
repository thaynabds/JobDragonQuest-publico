package dev.jobdragonquest.notifications;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import dev.jobdragonquest.applications.ApplicationDtos.ApplicationResponse;
import dev.jobdragonquest.applications.ApplicationService;
import dev.jobdragonquest.applications.ApplicationService.PendingSuggestion;
import dev.jobdragonquest.applications.ApplicationState;
import dev.jobdragonquest.sources.SourceDtos.SourceResponse;
import dev.jobdragonquest.sources.SourceService;

/**
 * Transforma pendências e prazos em avisos. Roda quando o painel ou o MCP consulta os avisos, no máximo uma vez por
 * intervalo para cada pessoa. A chave de cada aviso identifica o fato (candidatura, etapa, prazo, antecedência), então
 * rodar de novo nunca repete aviso.
 */
@Component
class NotificationGenerator {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final ApplicationService applications;
    private final SourceService sources;
    private final NotificationService notifications;
    private final NotificationPreferences preferences;
    private final NotificationProperties properties;
    private final Clock clock;
    private final Map<UUID, Instant> lastRun = new ConcurrentHashMap<>();

    NotificationGenerator(ApplicationService applications, SourceService sources, NotificationService notifications,
            NotificationPreferences preferences, NotificationProperties properties, Clock clock) {
        this.applications = applications;
        this.sources = sources;
        this.notifications = notifications;
        this.preferences = preferences;
        this.properties = properties;
        this.clock = clock;
    }

    void refresh(UUID userId) {
        Instant now = clock.instant();
        Instant previous = lastRun.get(userId);
        if (previous != null && previous.plus(properties.refreshInterval()).isAfter(now)) {
            return;
        }
        lastRun.put(userId, now);
        ZoneId zone = preferences.zone(userId);
        List<Integer> interviewLeads = leads(userId, NotificationType.INTERVIEW_REMINDER);
        List<Integer> deadlineLeads = leads(userId, NotificationType.DEADLINE_REMINDER);
        Integer staleDays = preferences.preference(userId, NotificationType.NO_UPDATE.name()).staleDays();

        List<ApplicationResponse> all = applications.all(userId);
        for (ApplicationResponse app : all) {
            if (app.state().isFinal()) {
                continue;
            }
            if (app.state() == ApplicationState.AWAITING_USER) {
                record(userId, app, NotificationType.ACTION_REQUIRED, "action:" + app.id() + ":" + epoch(app.stateChangedAt()),
                        name(app) + ": " + orDefault(app.nextAction(), "a candidatura espera uma ação sua"), app.nextAction(),
                        null);
            }
            if (app.state() == ApplicationState.TECH_TEST_PENDING) {
                record(userId, app, NotificationType.TECH_TEST, "tech-test:" + app.id() + ":" + epoch(app.stateChangedAt()),
                        "Teste técnico pendente: " + name(app) + (app.nextAction() == null ? "" : " — " + app.nextAction()),
                        app.nextAction(), null);
            }
            if (app.nextDueAt() != null && app.nextDueAt().isAfter(now)) {
                boolean interview = app.state() == ApplicationState.INTERVIEW_SCHEDULED;
                Integer lead = reachedLead(interview ? interviewLeads : deadlineLeads, app.nextDueAt(), now);
                if (lead != null) {
                    NotificationType type = interview ? NotificationType.INTERVIEW_REMINDER : NotificationType.DEADLINE_REMINDER;
                    String when = WHEN.format(app.nextDueAt().atZone(zone));
                    String message = interview ? "Entrevista com " + name(app) + " em " + when
                            : "Prazo de " + name(app) + " em " + when + (app.nextAction() == null ? "" : ": " + app.nextAction());
                    record(userId, app, type, "reminder:" + type + ":" + app.id() + ":" + epoch(app.nextDueAt()) + ":" + lead,
                            message, app.nextAction(), lead);
                }
            }
            if (staleDays != null && app.state().isActive() && app.lastEvidenceAt() != null
                    && !app.lastEvidenceAt().plus(Duration.ofDays(staleDays)).isAfter(now)) {
                long days = Duration.between(app.lastEvidenceAt(), now).toDays();
                record(userId, app, NotificationType.NO_UPDATE,
                        "stale:" + app.id() + ":" + epoch(app.lastEvidenceAt()) + ":" + staleDays,
                        name(app) + " está sem atualização há " + days + " dias", null, null);
            }
        }
        for (PendingSuggestion suggestion : applications.pendingSuggestions(userId)) {
            ApplicationResponse app = all.stream().filter(a -> a.id().equals(suggestion.applicationId())).findFirst().orElse(null);
            if (app != null) {
                notifications.record(userId, new NotificationDraft(NotificationType.SUGGESTION_TO_REVIEW.name(),
                        NotificationType.SUGGESTION_TO_REVIEW, NotificationCatalog.priority(NotificationType.SUGGESTION_TO_REVIEW.name()),
                        "suggestion:" + suggestion.eventId(), app.id(), null, suggestion.toState(), app.company(), app.title(),
                        null, "Confirme a mudança de " + name(app) + " para " + NotificationCatalog.stageLabel(suggestion.toState()),
                        null, null, null));
            }
        }
        for (SourceResponse source : sources.failing(userId)) {
            notifications.record(userId, sourceDraft(NotificationType.SOURCE_FAILING, source,
                    "source-failing:" + source.id() + ":" + epoch(source.lastRunAt()),
                    "A coleta da fonte " + source.name() + " falhou: " + orDefault(source.lastMessage(), "erro desconhecido"),
                    source.lastMessage()));
        }
        for (SourceResponse source : sources.dueForCheck(userId)) {
            notifications.record(userId, sourceDraft(NotificationType.SOURCE_TO_CHECK, source,
                    "source-check:" + source.id() + ":" + epoch(source.nextRunAt()),
                    "Hora de conferir novas vagas em " + source.name() + " (" + source.kindLabel() + ")", null));
        }
        notifications.purgeOld(userId, properties.retention());
    }

    /** A menor antecedência já alcançada: se o painel ficou fechado, só o lembrete mais próximo do prazo aparece. */
    static Integer reachedLead(List<Integer> leads, Instant due, Instant now) {
        if (leads == null) {
            return null;
        }
        return leads.stream().sorted(Comparator.naturalOrder())
                .filter(lead -> !now.isBefore(due.minus(Duration.ofMinutes(lead))))
                .findFirst().orElse(null);
    }

    private List<Integer> leads(UUID userId, NotificationType type) {
        return preferences.preference(userId, type.name()).leadMinutes();
    }

    private void record(UUID userId, ApplicationResponse app, NotificationType type, String key, String message,
            String detail, Integer lead) {
        notifications.record(userId, new NotificationDraft(type.name(), type, NotificationCatalog.priority(type.name()), key,
                app.id(), null, app.state(), app.company(), app.title(), detail, message, app.nextDueAt(), null, lead));
    }

    private static NotificationDraft sourceDraft(NotificationType type, SourceResponse source, String key, String message,
            String detail) {
        return new NotificationDraft(type.name(), type, NotificationCatalog.priority(type.name()), key, null, source.id(), null,
                source.kindLabel(), source.name(), detail, message, source.nextRunAt(), null, null);
    }

    static String name(ApplicationResponse app) {
        String company = app.company() == null || app.company().isBlank() ? null : app.company();
        String title = app.title() == null || app.title().isBlank() ? null : app.title();
        if (company != null && title != null) {
            return company + " (" + title + ")";
        }
        return company != null ? company : title != null ? title : "Candidatura";
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static long epoch(Instant value) {
        return value == null ? 0 : value.getEpochSecond();
    }
}
