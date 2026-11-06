package dev.jobfly.dashboard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jobfly.applications.ApplicationDtos.ApplicationResponse;
import dev.jobfly.applications.ApplicationService;
import dev.jobfly.applications.ApplicationService.PendingSuggestion;
import dev.jobfly.applications.ApplicationState;
import dev.jobfly.jobs.JobService;

/** Visão geral e central de pendências (RF-07.1, RF-07.4). Tudo é calculado na hora, sem tabela própria. */
@Service
public class DashboardService {

    static final Duration INTERVIEW_HORIZON = Duration.ofDays(7);
    static final Duration DEADLINE_HORIZON = Duration.ofDays(3);

    private final ApplicationService applications;
    private final JobService jobs;
    private final Clock clock;

    DashboardService(ApplicationService applications, JobService jobs, Clock clock) {
        this.applications = applications;
        this.jobs = jobs;
        this.clock = clock;
    }

    public enum Priority {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum PendingType {
        ACTION_REQUIRED,
        TECH_TEST,
        INTERVIEW_SOON,
        DEADLINE_SOON,
        SUGGESTION_TO_REVIEW,
        NO_UPDATE
    }

    public record PendingItem(PendingType type, Priority priority, UUID applicationId, UUID eventId, String company,
            String title, String message, Instant dueAt) {
    }

    public record Summary(long jobsFound, long applicationsTotal, long submitted, long inProgress,
            long interviewsScheduled, long testsPending, long offers, long approved, long rejected, long stale,
            long pendingItems, Map<ApplicationState, Long> byState) {
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID userId) {
        List<ApplicationResponse> all = applications.all(userId);
        Map<ApplicationState, Long> byState = new EnumMap<>(ApplicationState.class);
        for (ApplicationState state : ApplicationState.values()) {
            byState.put(state, 0L);
        }
        all.forEach(app -> byState.merge(app.state(), 1L, Long::sum));
        long submitted = all.stream().filter(DashboardService::wasSubmitted).count();
        long inProgress = all.stream().filter(app -> app.state().isActive()).count();
        long stale = all.stream().filter(ApplicationResponse::stale).count();
        return new Summary(jobs.count(userId), all.size(), submitted, inProgress,
                byState.get(ApplicationState.INTERVIEW_SCHEDULED), byState.get(ApplicationState.TECH_TEST_PENDING),
                byState.get(ApplicationState.OFFER_RECEIVED), byState.get(ApplicationState.APPROVED),
                byState.get(ApplicationState.REJECTED), stale, pending(userId, all).size(), Map.copyOf(byState));
    }

    @Transactional(readOnly = true)
    public List<PendingItem> pending(UUID userId) {
        return pending(userId, applications.all(userId));
    }

    private List<PendingItem> pending(UUID userId, List<ApplicationResponse> all) {
        Instant now = clock.instant();
        List<PendingItem> items = new ArrayList<>();
        for (ApplicationResponse app : all) {
            if (app.state() == ApplicationState.AWAITING_USER) {
                items.add(item(PendingType.ACTION_REQUIRED, Priority.HIGH, app,
                        app.nextAction() == null ? "A candidatura espera uma ação sua" : app.nextAction()));
            }
            if (app.state() == ApplicationState.TECH_TEST_PENDING) {
                items.add(item(PendingType.TECH_TEST, Priority.HIGH, app,
                        app.nextAction() == null ? "Teste técnico pendente" : app.nextAction()));
            }
            if (app.nextDueAt() != null && !app.state().isFinal() && app.nextDueAt().isAfter(now)) {
                if (app.state() == ApplicationState.INTERVIEW_SCHEDULED
                        && app.nextDueAt().isBefore(now.plus(INTERVIEW_HORIZON))) {
                    items.add(item(PendingType.INTERVIEW_SOON, Priority.HIGH, app, "Entrevista em breve"));
                } else if (app.nextDueAt().isBefore(now.plus(DEADLINE_HORIZON))) {
                    items.add(item(PendingType.DEADLINE_SOON, Priority.MEDIUM, app,
                            app.nextAction() == null ? "Prazo próximo" : app.nextAction()));
                }
            }
            if (app.stale()) {
                items.add(item(PendingType.NO_UPDATE, Priority.LOW, app, "Sem atualização desde " + app.lastEvidenceAt()));
            }
        }
        for (PendingSuggestion suggestion : applications.pendingSuggestions(userId)) {
            ApplicationResponse app = all.stream().filter(a -> a.id().equals(suggestion.applicationId())).findFirst().orElse(null);
            if (app != null) {
                items.add(new PendingItem(PendingType.SUGGESTION_TO_REVIEW, Priority.MEDIUM, app.id(), suggestion.eventId(),
                        app.company(), app.title(), "Confirmar mudança para " + suggestion.toState() + " (confiança "
                                + suggestion.confidence() + ")", null));
            }
        }
        items.sort(Comparator.comparing(PendingItem::priority)
                .thenComparing(PendingItem::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PendingItem::company, Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(items);
    }

    /** "Encerrado" pode acontecer antes do envio (vaga saiu do ar). Nesse caso só conta se houver data de envio. */
    private static boolean wasSubmitted(ApplicationResponse app) {
        ApplicationState state = app.state();
        return state.isActive() || state == ApplicationState.APPROVED || state == ApplicationState.REJECTED
                || (state == ApplicationState.CLOSED && app.appliedAt() != null);
    }

    private static PendingItem item(PendingType type, Priority priority, ApplicationResponse app, String message) {
        return new PendingItem(type, priority, app.id(), null, app.company(), app.title(), message, app.nextDueAt());
    }
}
