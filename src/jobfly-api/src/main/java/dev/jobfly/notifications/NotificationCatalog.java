package dev.jobfly.notifications;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import dev.jobfly.applications.ApplicationState;

/**
 * Tópicos configuráveis e seus padrões.
 *
 * <ul>
 *   <li>{@code STAGE:<ETAPA>}: a candidatura mudou de etapa sem ação da pessoa (e-mail, agente, sistema).</li>
 *   <li>{@code <TIPO>}: pendências e lembretes (ação exigida, teste, entrevista, prazo, sugestão, fontes, sem atualização).</li>
 *   <li>{@code NEW_JOBS:<id da fonte>}: vagas novas coletadas por uma fonte vigiada, opcionalmente acima de uma nota.</li>
 * </ul>
 */
public final class NotificationCatalog {

    public static final String STAGE_PREFIX = "STAGE:";
    public static final String NEW_JOBS_PREFIX = "NEW_JOBS:";
    public static final String TEST_TOPIC = "TEST";

    /** Tipos com tópico próprio (as etapas e as fontes são tópicos à parte). */
    public static final List<NotificationType> PENDING_TYPES = List.of(NotificationType.ACTION_REQUIRED,
            NotificationType.TECH_TEST, NotificationType.INTERVIEW_REMINDER, NotificationType.DEADLINE_REMINDER,
            NotificationType.SUGGESTION_TO_REVIEW, NotificationType.SOURCE_FAILING, NotificationType.SOURCE_TO_CHECK,
            NotificationType.NO_UPDATE);

    private static final Pattern NEW_JOBS_TOPIC = Pattern.compile(
            "NEW_JOBS:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private static final Set<ApplicationState> HIGH_STAGES = EnumSet.of(ApplicationState.AWAITING_USER,
            ApplicationState.RECRUITER_CONTACT, ApplicationState.INTERVIEW_SCHEDULED, ApplicationState.TECH_TEST_PENDING,
            ApplicationState.OFFER_RECEIVED, ApplicationState.APPROVED);
    private static final Set<ApplicationState> LOW_STAGES = EnumSet.of(ApplicationState.JOB_FOUND,
            ApplicationState.JOB_ANALYZED, ApplicationState.RECOMMENDED, ApplicationState.PREPARED);

    private static final Map<NotificationType, NotificationPriority> TYPE_PRIORITY = new EnumMap<>(Map.of(
            NotificationType.ACTION_REQUIRED, NotificationPriority.HIGH,
            NotificationType.TECH_TEST, NotificationPriority.HIGH,
            NotificationType.INTERVIEW_REMINDER, NotificationPriority.HIGH,
            NotificationType.DEADLINE_REMINDER, NotificationPriority.MEDIUM,
            NotificationType.SUGGESTION_TO_REVIEW, NotificationPriority.MEDIUM,
            NotificationType.SOURCE_FAILING, NotificationPriority.MEDIUM,
            NotificationType.SOURCE_TO_CHECK, NotificationPriority.LOW,
            NotificationType.NO_UPDATE, NotificationPriority.LOW,
            NotificationType.NEW_JOBS, NotificationPriority.MEDIUM,
            NotificationType.TEST, NotificationPriority.HIGH));

    static final List<Integer> INTERVIEW_LEADS = List.of(1440, 60);
    static final List<Integer> DEADLINE_LEADS = List.of(4320, 1440);
    static final int DEFAULT_STALE_DAYS = 14;

    private NotificationCatalog() {
    }

    public static String stageTopic(ApplicationState state) {
        return STAGE_PREFIX + state.name();
    }

    public static String newJobsTopic(UUID sourceId) {
        return NEW_JOBS_PREFIX + sourceId;
    }

    /** Tópicos fixos, na ordem em que o painel mostra: etapas e depois pendências. */
    public static List<String> fixedTopics() {
        List<String> topics = new ArrayList<>();
        for (ApplicationState state : ApplicationState.values()) {
            topics.add(stageTopic(state));
        }
        PENDING_TYPES.forEach(type -> topics.add(type.name()));
        return List.copyOf(topics);
    }

    public static boolean isFixed(String topic) {
        return fixedTopics().contains(topic);
    }

    public static boolean isNewJobs(String topic) {
        return topic != null && NEW_JOBS_TOPIC.matcher(topic).matches();
    }

    public static Optional<UUID> sourceOf(String topic) {
        return isNewJobs(topic) ? Optional.of(UUID.fromString(topic.substring(NEW_JOBS_PREFIX.length()))) : Optional.empty();
    }

    public static Optional<ApplicationState> stageOf(String topic) {
        if (topic == null || !topic.startsWith(STAGE_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(ApplicationState.valueOf(topic.substring(STAGE_PREFIX.length())));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static NotificationPriority priority(String topic) {
        Optional<ApplicationState> stage = stageOf(topic);
        if (stage.isPresent()) {
            if (HIGH_STAGES.contains(stage.get())) {
                return NotificationPriority.HIGH;
            }
            return LOW_STAGES.contains(stage.get()) ? NotificationPriority.LOW : NotificationPriority.MEDIUM;
        }
        if (isNewJobs(topic)) {
            return NotificationPriority.MEDIUM;
        }
        if (TEST_TOPIC.equals(topic)) {
            return NotificationPriority.HIGH;
        }
        return TYPE_PRIORITY.getOrDefault(NotificationType.valueOf(topic), NotificationPriority.MEDIUM);
    }

    public static boolean supportsLead(String topic) {
        return NotificationType.INTERVIEW_REMINDER.name().equals(topic) || NotificationType.DEADLINE_REMINDER.name().equals(topic);
    }

    public static boolean supportsStaleDays(String topic) {
        return NotificationType.NO_UPDATE.name().equals(topic);
    }

    public static boolean supportsMinScore(String topic) {
        return isNewJobs(topic);
    }

    /** Padrão por prioridade: alta avisa em tudo, média só no painel, baixa só no sino. */
    public static NotificationPreferences.Preference defaults(String topic) {
        NotificationPriority priority = priority(topic);
        boolean high = priority == NotificationPriority.HIGH;
        boolean popup = priority != NotificationPriority.LOW;
        List<Integer> leads = NotificationType.INTERVIEW_REMINDER.name().equals(topic) ? INTERVIEW_LEADS
                : NotificationType.DEADLINE_REMINDER.name().equals(topic) ? DEADLINE_LEADS : null;
        Integer staleDays = supportsStaleDays(topic) ? DEFAULT_STALE_DAYS : null;
        return new NotificationPreferences.Preference(topic, true, popup, high, high, leads, null, staleDays);
    }

    /** Nome da etapa em português, usado na mensagem pronta (o painel traduz pelos campos estruturados). */
    public static String stageLabel(ApplicationState state) {
        return switch (state) {
            case JOB_FOUND -> "Vaga encontrada";
            case JOB_ANALYZED -> "Vaga analisada";
            case RECOMMENDED -> "Recomendada";
            case PREPARED -> "Preparada";
            case AWAITING_USER -> "Aguardando você";
            case SUBMITTED -> "Candidatura enviada";
            case CONFIRMED -> "Recebimento confirmado";
            case SCREENING -> "Em triagem";
            case RECRUITER_CONTACT -> "Contato do recrutador";
            case INTERVIEW_SCHEDULED -> "Entrevista agendada";
            case INTERVIEW_DONE -> "Entrevista realizada";
            case TECH_TEST_PENDING -> "Teste técnico pendente";
            case TECH_TEST_DONE -> "Teste técnico feito";
            case BEHAVIORAL_ASSESSMENT -> "Avaliação comportamental";
            case ADDITIONAL_STAGE -> "Etapa adicional";
            case OFFER_RECEIVED -> "Proposta recebida";
            case APPROVED -> "Aprovada";
            case REJECTED -> "Não aprovada";
            case CLOSED -> "Encerrada";
        };
    }
}
