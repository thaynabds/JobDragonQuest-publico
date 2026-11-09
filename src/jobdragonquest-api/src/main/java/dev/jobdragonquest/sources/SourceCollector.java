package dev.jobdragonquest.sources;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jobdragonquest.applications.ApplicationDtos.CreateApplicationRequest;
import dev.jobdragonquest.applications.ApplicationEnums.Origin;
import dev.jobdragonquest.applications.ApplicationService;
import dev.jobdragonquest.applications.ApplicationState;
import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.error.NotFoundException;
import dev.jobdragonquest.common.text.TitleMatcher;
import dev.jobdragonquest.common.text.TitleSeniority;
import dev.jobdragonquest.jobs.JobDtos.JobCreated;
import dev.jobdragonquest.jobs.JobDtos.JobRequest;
import dev.jobdragonquest.jobs.JobDtos.RequirementDto;
import dev.jobdragonquest.jobs.JobRequirement;
import dev.jobdragonquest.jobs.JobService;
import dev.jobdragonquest.matching.MatchResult;
import dev.jobdragonquest.matching.MatchingEngine;
import dev.jobdragonquest.matching.PreferenceFilters;
import dev.jobdragonquest.profiles.ProfileEnums.AutomationMode;
import dev.jobdragonquest.profiles.ProfileService;
import dev.jobdragonquest.profiles.ProfileSnapshot;
import dev.jobdragonquest.sources.SourceDtos.RunResponse;
import dev.jobdragonquest.sources.SourceKind.CollectionMode;
import dev.jobdragonquest.sources.SourceRun.Trigger;
import dev.jobdragonquest.sources.WatchedSource.RunStatus;
import dev.jobdragonquest.sources.collect.AtsClient;
import dev.jobdragonquest.sources.collect.CollectedJob;
import dev.jobdragonquest.sources.collect.CollectionException;
import dev.jobdragonquest.sources.collect.SkillCatalog;

/**
 * Coleta vagas das fontes automáticas (ATS com API pública).
 * <ol>
 *   <li>Busca as vagas do board.</li>
 *   <li>Filtra pelas palavras-chave da fonte ou, sem elas, pelos cargos desejados dos perfis ligados.</li>
 *   <li>Grava cada vaga, sem duplicar, com as tecnologias citadas como requisitos "mencionados".</li>
 *   <li>Analisa a vaga com os perfis ligados e, quando o melhor recomenda candidatura, cria uma candidatura RECOMMENDED.</li>
 * </ol>
 * Uma vaga inválida (ex.: URL não http) é pulada sem derrubar a coleta. Falha do ATS registra o erro e recua.
 */
@Service
public class SourceCollector {

    static final int MAX_JOBS_PER_RUN = 200;
    static final int MAX_REQUIREMENTS = 40;
    static final int DUE_BATCH = 5;
    /** Validade da reserva de uma coleta. Passado esse tempo, uma coleta interrompida não trava mais a fonte. */
    static final Duration LEASE = Duration.ofMinutes(15);
    private static final Logger log = LoggerFactory.getLogger(SourceCollector.class);

    private final WatchedSourceRepository sources;
    private final SourceRunRepository runs;
    private final List<AtsClient> clients;
    private final SkillCatalog skills;
    private final JobService jobs;
    private final ProfileService profiles;
    private final MatchingEngine matching;
    private final ApplicationService applications;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher publisher;

    SourceCollector(WatchedSourceRepository sources, SourceRunRepository runs, List<AtsClient> clients, SkillCatalog skills,
            JobService jobs, ProfileService profiles, MatchingEngine matching, ApplicationService applications, Clock clock,
            PlatformTransactionManager txManager, ApplicationEventPublisher publisher) {
        this.sources = sources;
        this.runs = runs;
        this.clients = List.copyOf(clients);
        this.skills = skills;
        this.jobs = jobs;
        this.profiles = profiles;
        this.matching = matching;
        this.applications = applications;
        this.clock = clock;
        this.transaction = new TransactionTemplate(txManager);
        this.publisher = publisher;
    }

    /** Coleta pedida pela pessoa no painel. Confere a dona da fonte antes de qualquer coisa. */
    public RunResponse runNow(UUID userId, UUID sourceId) {
        WatchedSource source = transaction.execute(status -> sources.findByIdAndUserId(sourceId, userId)
                .orElseThrow(() -> new NotFoundException("Fonte")));
        if (source.getCollectionMode() != CollectionMode.AUTOMATIC) {
            throw DomainException.conflict("assisted_source",
                    source.getKind().label() + " não permite coleta automática: abra o link e registre as vagas encontradas");
        }
        if (!reserve(sourceId)) {
            throw DomainException.conflict("collection_running", "Já existe uma coleta desta fonte em andamento. Aguarde terminar.");
        }
        return collectReserved(sourceId, Trigger.MANUAL);
    }

    /** Ciclo do agendador: coleta as fontes automáticas vencidas, poucas por vez, reservando cada uma antes. */
    public int runDue() {
        Instant now = clock.instant();
        List<UUID> due = sources.findDueIds(CollectionMode.AUTOMATIC, now, PageRequest.of(0, DUE_BATCH));
        int collected = 0;
        for (UUID id : due) {
            if (reserve(id)) {
                collectReserved(id, Trigger.SCHEDULED);
                collected++;
            }
        }
        return collected;
    }

    /** Só uma coleta por fonte: a reserva é uma troca condicional no banco, válida entre threads e instâncias. */
    private boolean reserve(UUID sourceId) {
        Instant now = clock.instant();
        Integer reserved = transaction.execute(status -> sources.reserve(sourceId, now, now.plus(LEASE)));
        return reserved != null && reserved == 1;
    }

    private RunResponse collectReserved(UUID sourceId, Trigger trigger) {
        try {
            return collect(sourceId, trigger);
        } finally {
            transaction.executeWithoutResult(status -> sources.release(sourceId));
        }
    }

    RunResponse collect(UUID sourceId, Trigger trigger) {
        Instant startedAt = clock.instant();
        Plan plan = transaction.execute(status -> {
            WatchedSource source = sources.findById(sourceId).orElseThrow(() -> new NotFoundException("Fonte"));
            List<ProfileSnapshot> linked = profiles.snapshots(source.getUserId()).stream()
                    .filter(profile -> source.getProfileIds().contains(profile.id()))
                    .toList();
            List<String> terms = !source.getKeywords().isEmpty() ? source.getKeywords()
                    : linked.stream().flatMap(profile -> profile.desiredTitles().stream()).distinct().toList();
            return new Plan(source.getUserId(), source.getKind(), source.getIdentifier(), source.getName(), linked, terms);
        });
        // Resolvido na hora da coleta: não depende da ordem em que os adaptadores foram criados.
        AtsClient client = clients.stream().filter(candidate -> candidate.kind() == plan.kind()).findFirst().orElse(null);
        List<CollectedJob> fetched;
        try {
            if (client == null) {
                throw new CollectionException("Sem adaptador para " + plan.kind().label());
            }
            fetched = client.fetch(plan.identifier());
        } catch (CollectionException ex) {
            log.warn("Coleta da fonte {} falhou: {}", sourceId, ex.getMessage());
            return finish(sourceId, trigger, RunStatus.FAILED, 0, 0, 0, ex.getMessage(), startedAt);
        }

        List<CollectedJob> byTitle = fetched.stream()
                .filter(job -> plan.terms().isEmpty() || plan.terms().stream().anyMatch(term -> TitleMatcher.containsAllWords(job.title(), term)))
                .toList();
        // Empresa excluída, palavra excluída ou nenhuma palavra obrigatória em TODOS os perfis ligados: a vaga nem entra.
        List<CollectedJob> wanted = byTitle.stream().filter(job -> !filteredOut(plan, job)).toList();
        int filtered = byTitle.size() - wanted.size();
        List<CollectedJob> relevant = wanted.stream().limit(MAX_JOBS_PER_RUN).toList();
        int created = 0;
        int recommended = 0;
        int skipped = 0;
        List<Integer> scores = new ArrayList<>();
        for (CollectedJob job : relevant) {
            JobCreated result;
            try {
                result = jobs.createFromSource(plan.userId(), sourceId, toRequest(plan, job));
            } catch (DomainException ex) {
                skipped++;
                continue;
            } catch (DataIntegrityViolationException ex) {
                // Outra gravação da mesma vaga venceu a corrida (restrição única): ela já existe, então conta como duplicada.
                continue;
            }
            if (result.duplicate()) {
                continue;
            }
            created++;
            try {
                MatchResult best = plan.profiles().isEmpty() ? null
                        : matching.rank(plan.profiles(), jobs.snapshot(plan.userId(), result.job().id())).getFirst();
                scores.add(best == null ? null : best.score());
                if (recommend(plan, result.job().id(), best)) {
                    recommended++;
                }
            } catch (DomainException ex) {
                log.warn("Vaga {} criada, mas a recomendação falhou: {}", result.job().id(), ex.code());
            }
        }
        String message = fetched.size() + " vaga(s) no board, " + relevant.size() + " relevante(s), " + created
                + " nova(s), " + recommended + " recomendada(s)" + (skipped > 0 ? ", " + skipped + " ignorada(s) por dados inválidos" : "")
                + (filtered > 0 ? ", " + filtered + " fora das preferências do perfil" : "");
        RunResponse response = finish(sourceId, trigger, RunStatus.OK, relevant.size(), created, recommended, message, startedAt);
        if (created > 0) {
            publisher.publishEvent(new JobsCollectedEvent(plan.userId(), sourceId, plan.companyName(), scores, response.id()));
        }
        return response;
    }

    private static boolean filteredOut(Plan plan, CollectedJob job) {
        return !plan.profiles().isEmpty() && plan.profiles().stream().allMatch(profile ->
                !PreferenceFilters.hardBlockers(profile.filters(), plan.companyName(), job.title(), job.description()).isEmpty());
    }

    /**
     * Vaga compatível vira candidatura conforme o modo do perfil: OFF não cria nada, REVIEW cria como recomendada (fila
     * de revisão) e AUTOMATIC já cria como preparada. O envio para a empresa é sempre feito pela pessoa.
     */
    private boolean recommend(Plan plan, UUID jobId, MatchResult best) {
        if (best == null || best.recommendation() != MatchResult.Recommendation.APPLY) {
            return false;
        }
        AutomationMode mode = plan.profiles().stream().filter(profile -> profile.id().equals(best.profileId()))
                .map(ProfileSnapshot::automationMode).findFirst().orElse(AutomationMode.REVIEW);
        if (mode == AutomationMode.OFF) {
            return false;
        }
        ApplicationState state = mode == AutomationMode.AUTOMATIC ? ApplicationState.PREPARED : ApplicationState.RECOMMENDED;
        String note = mode == AutomationMode.AUTOMATIC ? best.summary() + " Preparada pela automação do perfil: revise e envie."
                : best.summary();
        applications.create(plan.userId(), new CreateApplicationRequest(jobId, best.profileId(), Origin.AUTOMATION,
                plan.kind().label(), null, null, null, null, null, state, note, null, null), "src-" + jobId);
        return true;
    }

    private JobRequest toRequest(Plan plan, CollectedJob job) {
        Set<String> mentioned = skills.detect(job.title() + "\n" + (job.description() == null ? "" : job.description()));
        List<RequirementDto> requirements = mentioned.stream()
                .limit(MAX_REQUIREMENTS)
                .map(skill -> new RequirementDto(JobRequirement.Kind.DESIRED, "Mencionado na descrição: " + skill, skill, null))
                .toList();
        return new JobRequest(plan.kind().name(), job.url(), plan.companyName(), job.title(), job.location(), job.country(),
                job.workMode(), TitleSeniority.infer(job.title()), job.description(), null, null, null, null, requirements, List.of());
    }

    private RunResponse finish(UUID sourceId, Trigger trigger, RunStatus status, int found, int created, int recommended,
            String message, Instant startedAt) {
        return transaction.execute(tx -> {
            Instant now = clock.instant();
            WatchedSource source = sources.findById(sourceId).orElseThrow(() -> new NotFoundException("Fonte"));
            if (status == RunStatus.FAILED) {
                source.recordFailure(message, now);
            } else {
                source.recordSuccess(status, found, message, now);
            }
            SourceRun run = runs.save(new SourceRun(sourceId, trigger, status, found, created, recommended, message, startedAt, now));
            return RunResponse.of(run);
        });
    }

    private record Plan(UUID userId, SourceKind kind, String identifier, String companyName, List<ProfileSnapshot> profiles,
            List<String> terms) {
    }
}
