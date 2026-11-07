package dev.jobdragonquest.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.jobdragonquest.applications.StageChangedEvent;
import dev.jobdragonquest.sources.JobsCollectedEvent;

/**
 * Escuta os eventos de domínio depois do commit. Uma falha aqui só é registrada no log: nunca transforma uma mudança
 * de etapa ou uma coleta bem-sucedida em erro.
 */
@Component
class NotificationListeners {

    private static final Logger log = LoggerFactory.getLogger(NotificationListeners.class);

    private final NotificationService notifications;
    private final NotificationPreferences preferences;

    NotificationListeners(NotificationService notifications, NotificationPreferences preferences) {
        this.notifications = notifications;
        this.preferences = preferences;
    }

    /** Só mudanças que a pessoa não fez (e-mail, agente, portal, sistema): quem mexeu no card já sabe da mudança. */
    @TransactionalEventListener(fallbackExecution = true)
    void onStageChanged(StageChangedEvent event) {
        if (event.source().isHuman()) {
            return;
        }
        try {
            String topic = NotificationCatalog.stageTopic(event.toState());
            String who = event.company() == null ? (event.title() == null ? "Candidatura" : event.title())
                    : event.company() + (event.title() == null ? "" : " (" + event.title() + ")");
            notifications.record(event.userId(), new NotificationDraft(topic, NotificationType.STAGE_CHANGED,
                    NotificationCatalog.priority(topic), "stage:" + event.eventId(), event.applicationId(), null,
                    event.toState(), event.company(), event.title(), null,
                    who + ": etapa mudou para " + NotificationCatalog.stageLabel(event.toState()), null, null, null));
        } catch (RuntimeException ex) {
            log.warn("Não foi possível criar o aviso de mudança de etapa {}: {}", event.eventId(), ex.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onJobsCollected(JobsCollectedEvent event) {
        try {
            String topic = NotificationCatalog.newJobsTopic(event.sourceId());
            Integer minScore = preferences.preference(event.userId(), topic).minScore();
            long count = event.scores().stream()
                    .filter(score -> minScore == null || (score != null && score >= minScore))
                    .count();
            if (count == 0) {
                return;
            }
            String message = count + (count == 1 ? " vaga nova em " : " vagas novas em ") + event.sourceName()
                    + (minScore == null ? "" : " com nota a partir de " + minScore);
            notifications.record(event.userId(), new NotificationDraft(topic, NotificationType.NEW_JOBS,
                    NotificationCatalog.priority(topic), "jobs:" + event.runId(), null, event.sourceId(), null, null,
                    event.sourceName(), null, message, null, (int) count, null));
        } catch (RuntimeException ex) {
            log.warn("Não foi possível criar o aviso de vagas novas da fonte {}: {}", event.sourceId(),
                    ex.getClass().getSimpleName());
        }
    }
}
