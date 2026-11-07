package dev.jobdragonquest.applications;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jobdragonquest.applications.ApplicationDtos.ApplicationDetail;
import dev.jobdragonquest.applications.ApplicationDtos.ApplicationResponse;
import dev.jobdragonquest.applications.ApplicationDtos.BoardColumn;
import dev.jobdragonquest.applications.ApplicationDtos.CreateApplicationRequest;
import dev.jobdragonquest.applications.ApplicationDtos.CreateResult;
import dev.jobdragonquest.applications.ApplicationDtos.EventResponse;
import dev.jobdragonquest.applications.ApplicationDtos.TransitionOutcome;
import dev.jobdragonquest.applications.ApplicationDtos.TransitionRequest;
import dev.jobdragonquest.applications.ApplicationDtos.TransitionResult;
import dev.jobdragonquest.applications.ApplicationDtos.UpdateApplicationRequest;
import dev.jobdragonquest.applications.ApplicationEnums.EventKind;
import dev.jobdragonquest.applications.ApplicationEnums.EventSource;
import dev.jobdragonquest.applications.ApplicationEnums.EventStatus;
import dev.jobdragonquest.applications.ApplicationEnums.Origin;
import dev.jobdragonquest.applications.JobApplication.Details;
import dev.jobdragonquest.audit.AuditService;
import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.error.NotFoundException;
import dev.jobdragonquest.common.text.TextNormalizer;
import dev.jobdragonquest.common.text.UrlCanonicalizer;
import dev.jobdragonquest.jobs.JobService;
import dev.jobdragonquest.jobs.JobSnapshot;
import dev.jobdragonquest.matching.MatchingEngine;
import dev.jobdragonquest.profiles.ProfileService;

/**
 * Central unificada de candidaturas: qualquer origem, sem duplicidade, com histórico de evidências.
 */
@Service
public class ApplicationService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_CARDS_PER_COLUMN = 50;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,100}");
    /** Tolerância para relógios dessincronizados ao aceitar datas de evento. */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final ApplicationEventPublisher publisher;

    private final ApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final ApplicationStateMachine stateMachine;
    private final JobService jobs;
    private final ProfileService profiles;
    private final MatchingEngine matching;
    private final AuditService audit;
    private final ApplicationProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    ApplicationService(ApplicationRepository applications, ApplicationEventRepository events,
            ApplicationStateMachine stateMachine, JobService jobs, ProfileService profiles, MatchingEngine matching,
            AuditService audit, ApplicationProperties properties, Clock clock, PlatformTransactionManager txManager,
            ApplicationEventPublisher publisher) {
        this.applications = applications;
        this.events = events;
        this.stateMachine = stateMachine;
        this.jobs = jobs;
        this.profiles = profiles;
        this.matching = matching;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(txManager);
        this.publisher = publisher;
    }

    /**
     * Cadastra a candidatura, ou devolve a existente se a mesma Idempotency-Key já foi usada ou se ela é duplicata
     * (mesma URL, ou mesma empresa e cargo dentro da janela configurada).
     * Se duas requisições com a mesma chave chegarem ao mesmo tempo, a restrição única do banco decide e a perdedora
     * devolve o registro da vencedora.
     */
    public CreateResult create(UUID userId, CreateApplicationRequest request, String idempotencyKey) {
        String key = validKey(idempotencyKey);
        try {
            return transaction.execute(status -> doCreate(userId, request, key));
        } catch (DataIntegrityViolationException ex) {
            if (key == null) {
                throw ex;
            }
            return transaction.execute(status -> applications.findByUserIdAndIdempotencyKey(userId, key)
                    .map(existing -> new CreateResult(response(existing), false, true))
                    .orElseThrow(() -> ex));
        }
    }

    private CreateResult doCreate(UUID userId, CreateApplicationRequest request, String key) {
        if (key != null) {
            Optional<JobApplication> replay = applications.findByUserIdAndIdempotencyKey(userId, key);
            if (replay.isPresent()) {
                return new CreateResult(response(replay.get()), false, true);
            }
        }
        Instant now = clock.instant();
        JobSnapshot job = request.jobId() == null ? null : jobs.snapshot(userId, request.jobId());
        if (request.profileId() != null) {
            profiles.requireOwned(userId, request.profileId());
        }
        Details details = details(request.jobId(), request.profileId(), request.platform(),
                firstNonBlank(request.company(), job == null ? null : job.company()),
                firstNonBlank(request.title(), job == null ? null : job.title()),
                firstNonBlank(request.url(), job == null ? null : job.canonicalUrl()),
                firstNonBlank(request.country(), job == null ? null : job.country()),
                notFuture(request.appliedAt(), now), request.notes(), request.nextAction(), request.nextDueAt());

        Optional<JobApplication> duplicate = findDuplicate(userId, details, now);
        if (duplicate.isPresent()) {
            return new CreateResult(response(duplicate.get()), true, false);
        }
        ApplicationState initial = initialState(request);
        Integer score = job != null && request.profileId() != null
                ? matching.evaluate(profiles.snapshot(userId, request.profileId()), job).score()
                : null;
        // Sem data informada, appliedAt fica vazio: o sistema não inventa quando a pessoa se candidatou.
        JobApplication application = new JobApplication(userId, details, request.origin(), initial, score, key, now);
        applications.saveAndFlush(application);
        ApplicationEvent created = events.save(new ApplicationEvent(application.getId(), EventKind.STATE_CHANGE, null, initial,
                sourceFor(request.origin()), "Cadastro (" + request.origin() + ")", BigDecimal.ONE, EventStatus.APPLIED,
                now, now));
        publisher.publishEvent(new StageChangedEvent(userId, application.getId(), created.getId(), null, initial,
                sourceFor(request.origin()), application.getCompany(), application.getTitle()));
        audit.record(userId, "application.created", "application", application.getId(), request.origin().name());
        return new CreateResult(response(application), false, false);
    }

    @Transactional
    public ApplicationResponse update(UUID userId, UUID applicationId, UpdateApplicationRequest request) {
        JobApplication application = load(userId, applicationId);
        if (request.version() != application.getVersion()) {
            throw DomainException.conflict("stale_version", "A candidatura mudou desde que foi aberta. Recarregue antes de salvar.");
        }
        if (request.profileId() != null) {
            profiles.requireOwned(userId, request.profileId());
        }
        Instant now = clock.instant();
        application.apply(details(application.getJobId(), request.profileId(), request.platform(), request.company(),
                request.title(), request.url(), request.country(), notFuture(request.appliedAt(), now), request.notes(),
                request.nextAction(), request.nextDueAt()), now);
        applications.flush();
        audit.record(userId, "application.updated", "application", applicationId, null);
        return response(application);
    }

    /**
     * Registra uma evidência e, se ela justificar, muda a etapa.
     * <ul>
     *   <li>Mesma etapa: só registra a evidência (o processo segue vivo) e a etapa não muda.</li>
     *   <li>Fonte automática com confiança abaixo do mínimo: vira sugestão para a pessoa decidir (RN-03).</li>
     *   <li>Transição proibida pela máquina de estados: 409, e nada é gravado.</li>
     * </ul>
     */
    @Transactional
    public TransitionResult transition(UUID userId, UUID applicationId, TransitionRequest request) {
        JobApplication application = load(userId, applicationId);
        Instant now = clock.instant();
        Instant occurredAt = request.occurredAt() == null ? now : notFuture(request.occurredAt(), now);
        BigDecimal confidence = confidence(request);
        ApplicationState from = application.getState();
        ApplicationState to = request.toState();

        if (from == to) {
            application.registerEvidence(occurredAt, now);
            application.planNext(request.nextAction(), request.nextDueAt(), now);
            ApplicationEvent event = events.save(new ApplicationEvent(applicationId, EventKind.NOTE, from, to,
                    request.source(), request.evidence().strip(), confidence, EventStatus.APPLIED, occurredAt, now));
            return result(TransitionOutcome.NOTED, application, event);
        }
        if (!stateMachine.canTransition(from, to, request.source())) {
            throw DomainException.conflict("invalid_transition",
                    "Mudança de " + from + " para " + to + " não permitida para a fonte " + request.source());
        }
        if (!request.source().isHuman() && confidence.compareTo(properties.minAutomaticConfidence()) < 0) {
            ApplicationEvent suggestion = events.save(new ApplicationEvent(applicationId, EventKind.SUGGESTION, from, to,
                    request.source(), request.evidence().strip(), confidence, EventStatus.PENDING, occurredAt, now));
            audit.record(userId, "application.suggestion_created", "application", applicationId, from + "->" + to);
            return result(TransitionOutcome.SUGGESTED, application, suggestion);
        }
        ApplicationEvent event = applyTransition(application, to, request.source(), request.evidence().strip(),
                confidence, occurredAt, now);
        application.planNext(request.nextAction(), request.nextDueAt(), now);
        audit.record(userId, "application.state_changed", "application", applicationId, from + "->" + to);
        return result(TransitionOutcome.APPLIED, application, event);
    }

    /** Aceitar uma sugestão confere de novo a transição: a etapa pode ter mudado desde que a sugestão foi criada. */
    @Transactional
    public TransitionResult acceptSuggestion(UUID userId, UUID applicationId, UUID eventId) {
        JobApplication application = load(userId, applicationId);
        ApplicationEvent suggestion = pendingSuggestion(applicationId, eventId);
        ApplicationState from = application.getState();
        if (from == suggestion.getToState() || !stateMachine.canTransition(from, suggestion.getToState(), EventSource.MANUAL)) {
            throw DomainException.conflict("suggestion_outdated", "A candidatura já mudou de etapa. Descarte esta sugestão.");
        }
        Instant now = clock.instant();
        suggestion.resolve(EventStatus.ACCEPTED);
        ApplicationEvent event = applyTransition(application, suggestion.getToState(), EventSource.MANUAL,
                "Sugestão aceita: " + suggestion.getEvidence(), BigDecimal.ONE, suggestion.getOccurredAt(), now);
        audit.record(userId, "application.suggestion_accepted", "application", applicationId, null);
        return result(TransitionOutcome.APPLIED, application, event);
    }

    @Transactional
    public void dismissSuggestion(UUID userId, UUID applicationId, UUID eventId) {
        load(userId, applicationId);
        pendingSuggestion(applicationId, eventId).resolve(EventStatus.DISMISSED);
        audit.record(userId, "application.suggestion_dismissed", "application", applicationId, null);
    }

    /** Anotação da pessoa: fica no histórico, mas não conta como evidência de andamento do processo. */
    @Transactional
    public EventResponse addNote(UUID userId, UUID applicationId, String text) {
        JobApplication application = load(userId, applicationId);
        Instant now = clock.instant();
        ApplicationEvent note = events.save(new ApplicationEvent(applicationId, EventKind.NOTE, application.getState(),
                application.getState(), EventSource.MANUAL, text.strip(), BigDecimal.ONE, EventStatus.APPLIED, now, now));
        return EventResponse.of(note);
    }

    @Transactional
    public void delete(UUID userId, UUID applicationId) {
        JobApplication application = load(userId, applicationId);
        applications.delete(application);
        audit.record(userId, "application.deleted", "application", applicationId, null);
    }

    @Transactional(readOnly = true)
    public ApplicationDetail detail(UUID userId, UUID applicationId) {
        JobApplication application = load(userId, applicationId);
        List<EventResponse> history = events.findByApplicationIdOrderByOccurredAtAscCreatedAtAsc(applicationId).stream()
                .map(EventResponse::of).toList();
        return new ApplicationDetail(response(application), history);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> list(UUID userId, ApplicationFilter filter, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")));
        return applications.findAll(filter.toSpecification(userId, staleThreshold()), pageable).map(this::response);
    }

    /** Kanban: todas as etapas, mesmo vazias, na ordem do processo. */
    @Transactional(readOnly = true)
    public List<BoardColumn> board(UUID userId, ApplicationFilter filter) {
        List<JobApplication> all = applications.findAll(filter.toSpecification(userId, staleThreshold()),
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")));
        Map<ApplicationState, List<JobApplication>> byState = new EnumMap<>(ApplicationState.class);
        all.forEach(app -> byState.computeIfAbsent(app.getState(), ignored -> new ArrayList<>()).add(app));
        return Arrays.stream(ApplicationState.values()).map(state -> {
            List<JobApplication> column = byState.getOrDefault(state, List.of());
            return new BoardColumn(state, column.size(),
                    column.stream().limit(MAX_CARDS_PER_COLUMN).map(this::response).toList());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> all(UUID userId) {
        return applications.findByUserId(userId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<PendingSuggestion> pendingSuggestions(UUID userId) {
        return events.findByUserAndKindAndStatus(userId, EventKind.SUGGESTION, EventStatus.PENDING).stream()
                .map(e -> new PendingSuggestion(e.getApplicationId(), e.getId(), e.getFromState(), e.getToState(),
                        e.getSource(), e.getEvidence(), e.getConfidence(), e.getCreatedAt()))
                .toList();
    }

    public record PendingSuggestion(UUID applicationId, UUID eventId, ApplicationState fromState, ApplicationState toState,
            EventSource source, String evidence, BigDecimal confidence, Instant createdAt) {
    }

    private ApplicationEvent applyTransition(JobApplication application, ApplicationState to, EventSource source,
            String evidence, BigDecimal confidence, Instant occurredAt, Instant now) {
        ApplicationState from = application.getState();
        application.moveTo(to, occurredAt, now);
        ApplicationEvent event = events.save(new ApplicationEvent(application.getId(), EventKind.STATE_CHANGE, from, to, source,
                evidence, confidence, EventStatus.APPLIED, occurredAt, now));
        publisher.publishEvent(new StageChangedEvent(application.getUserId(), application.getId(), event.getId(), from, to,
                source, application.getCompany(), application.getTitle()));
        return event;
    }

    private ApplicationEvent pendingSuggestion(UUID applicationId, UUID eventId) {
        ApplicationEvent event = events.findByIdAndApplicationId(eventId, applicationId)
                .orElseThrow(() -> new NotFoundException("Sugestão"));
        if (event.getKind() != EventKind.SUGGESTION || event.getStatus() != EventStatus.PENDING) {
            throw DomainException.conflict("suggestion_resolved", "Esta sugestão já foi decidida");
        }
        return event;
    }

    private Optional<JobApplication> findDuplicate(UUID userId, Details details, Instant now) {
        if (details.urlHash() != null) {
            Optional<JobApplication> byUrl = applications.findFirstByUserIdAndUrlHashOrderByCreatedAtDesc(userId, details.urlHash());
            if (byUrl.isPresent()) {
                return byUrl;
            }
        }
        return applications.findRecentSameRole(userId, details.companyNorm(), details.titleNorm(),
                now.minus(properties.duplicateWindow())).stream().findFirst();
    }

    /** Confere que a candidatura é da conta (404 se não for). */
    @Transactional(readOnly = true)
    public void requireOwned(UUID userId, UUID applicationId) {
        load(userId, applicationId);
    }

    /** Candidatura já registrada para a vaga (pela vaga ou pela mesma empresa e cargo na janela de duplicidade). */
    @Transactional(readOnly = true)
    public Optional<UUID> existingFor(UUID userId, UUID jobId, String company, String title) {
        Optional<UUID> byJob = applications.findByUserId(userId).stream()
                .filter(a -> jobId.equals(a.getJobId())).map(JobApplication::getId).findFirst();
        if (byJob.isPresent() || company == null || title == null) {
            return byJob;
        }
        return applications.findRecentSameRole(userId, TextNormalizer.normalizeCompany(company), TextNormalizer.normalize(title),
                clock.instant().minus(properties.duplicateWindow())).stream().map(JobApplication::getId).findFirst();
    }

    /** Vagas das candidaturas enviadas mais recentes (para a busca por vagas parecidas). */
    @Transactional(readOnly = true)
    public Set<UUID> recentSubmittedJobIds(UUID userId, int limit) {
        return applications.findByUserId(userId).stream()
                .filter(a -> a.getJobId() != null && a.getState().phase() != ApplicationState.Phase.PREPARATION)
                .sorted(Comparator.comparing((JobApplication a) -> a.getAppliedAt() != null ? a.getAppliedAt() : a.getCreatedAt())
                        .reversed())
                .limit(limit)
                .map(JobApplication::getJobId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Vagas que já têm candidatura, em qualquer etapa. */
    @Transactional(readOnly = true)
    public Set<UUID> jobIdsWithApplication(UUID userId) {
        return applications.findByUserId(userId).stream().map(JobApplication::getJobId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private JobApplication load(UUID userId, UUID applicationId) {
        return applications.findByIdAndUserId(applicationId, userId).orElseThrow(() -> new NotFoundException("Candidatura"));
    }

    private ApplicationResponse response(JobApplication a) {
        return new ApplicationResponse(a.getId(), a.getJobId(), a.getProfileId(), a.getOrigin(), a.getPlatform(),
                a.getCompany(), a.getTitle(), a.getJobUrl(), a.getCountry(), a.getAppliedAt(), a.getState(),
                a.getStateChangedAt(), a.getLastEvidenceAt(), a.isStale(clock.instant(), properties.staleAfter()),
                a.getNextAction(), a.getNextDueAt(), a.getMatchScore(), a.getNotes(), a.getCreatedAt(), a.getUpdatedAt(),
                a.getVersion());
    }

    private TransitionResult result(TransitionOutcome outcome, JobApplication application, ApplicationEvent event) {
        applications.flush();
        return new TransitionResult(outcome, response(application), EventResponse.of(event));
    }

    private Instant staleThreshold() {
        return clock.instant().minus(properties.staleAfter());
    }

    private static Details details(UUID jobId, UUID profileId, String platform, String company, String title, String url,
            String country, Instant appliedAt, String notes, String nextAction, Instant nextDueAt) {
        if (company == null || company.isBlank() || title == null || title.isBlank()) {
            throw DomainException.badRequest("company_title_required", "Informe empresa e cargo, ou uma vaga cadastrada");
        }
        String canonical = UrlCanonicalizer.optional(url);
        return new Details(jobId, profileId, blankToNull(platform), company.strip(), TextNormalizer.normalizeCompany(company),
                title.strip(), TextNormalizer.normalize(title), canonical, canonical == null ? null : UrlCanonicalizer.hash(canonical),
                country, appliedAt, blankToNull(notes), blankToNull(nextAction), nextDueAt);
    }

    private static ApplicationState initialState(CreateApplicationRequest request) {
        if (request.state() != null) {
            return request.state();
        }
        // Só a automação começa antes do envio. Nas outras origens a pessoa já se candidatou.
        return request.origin() == Origin.AUTOMATION ? ApplicationState.JOB_FOUND : ApplicationState.SUBMITTED;
    }

    private static EventSource sourceFor(Origin origin) {
        return switch (origin) {
            case AUTOMATION -> EventSource.SYSTEM;
            case EMAIL -> EventSource.EMAIL;
            case BROWSER_EXTENSION -> EventSource.EXTENSION;
            default -> EventSource.MANUAL;
        };
    }

    private BigDecimal confidence(TransitionRequest request) {
        if (request.source().isHuman()) {
            return BigDecimal.ONE;
        }
        if (request.confidence() == null) {
            throw DomainException.badRequest("confidence_required", "Fontes automáticas precisam informar a confiança (0 a 1)");
        }
        return request.confidence();
    }

    private static Instant notFuture(Instant value, Instant now) {
        if (value != null && value.isAfter(now.plus(CLOCK_SKEW))) {
            throw DomainException.badRequest("future_date", "A data do acontecimento não pode estar no futuro");
        }
        return value;
    }

    private static String validKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw DomainException.badRequest("invalid_idempotency_key", "Idempotency-Key: 8 a 100 caracteres [A-Za-z0-9_-]");
        }
        return key;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
