package dev.jobdragonquest.jobs;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jobdragonquest.audit.AuditService;
import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.error.NotFoundException;
import dev.jobdragonquest.common.text.SkillNormalizer;
import dev.jobdragonquest.common.text.TextNormalizer;
import dev.jobdragonquest.common.text.UrlCanonicalizer;
import dev.jobdragonquest.jobs.JobDtos.JobCreated;
import dev.jobdragonquest.jobs.JobDtos.JobRequest;
import dev.jobdragonquest.jobs.JobDtos.JobResponse;
import dev.jobdragonquest.jobs.JobDtos.JobSummary;
import dev.jobdragonquest.jobs.JobPosting.JobData;

@Service
public class JobService {

    static final int MAX_PAGE_SIZE = 100;

    private final JobRepository repository;
    private final AuditService audit;
    private final Clock clock;

    JobService(JobRepository repository, AuditService audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Cadastra a vaga ou devolve a existente. Há duplicata quando a URL canônica coincide ou quando empresa, cargo e
     * local normalizados coincidem (RF-03.2).
     */
    @Transactional
    public JobCreated create(UUID userId, JobRequest request) {
        return create(userId, request, null);
    }

    /** Igual a {@link #create(UUID, JobRequest)}, registrando qual fonte vigiada encontrou a vaga. */
    @Transactional
    public JobCreated createFromSource(UUID userId, UUID sourceId, JobRequest request) {
        return create(userId, request, sourceId);
    }

    private JobCreated create(UUID userId, JobRequest request, UUID sourceId) {
        JobData data = toData(request);
        Optional<JobPosting> existing = findDuplicate(userId, data);
        if (existing.isPresent()) {
            return new JobCreated(JobResponse.of(existing.get()), true);
        }
        JobPosting job = new JobPosting(userId, data, clock.instant());
        job.linkSource(sourceId);
        repository.saveAndFlush(job);
        audit.record(userId, "job.created", "job", job.getId(), job.getSource());
        return new JobCreated(JobResponse.of(job), false);
    }

    @Transactional
    public JobResponse update(UUID userId, UUID jobId, JobRequest request) {
        JobPosting job = load(userId, jobId);
        JobData data = toData(request);
        findDuplicate(userId, data).filter(other -> !other.getId().equals(jobId)).ifPresent(other -> {
            throw DomainException.conflict("job_duplicate", "Outra vaga já usa este endereço ou esta empresa, cargo e local");
        });
        job.apply(data, clock.instant());
        audit.record(userId, "job.updated", "job", jobId, null);
        return JobResponse.of(job);
    }

    @Transactional
    public JobResponse changeStatus(UUID userId, UUID jobId, JobStatus status) {
        JobPosting job = load(userId, jobId);
        job.changeStatus(status, clock.instant());
        audit.record(userId, "job.status_changed", "job", jobId, status.name());
        return JobResponse.of(job);
    }

    @Transactional(readOnly = true)
    public JobResponse get(UUID userId, UUID jobId) {
        return JobResponse.of(load(userId, jobId));
    }

    @Transactional(readOnly = true)
    public JobSnapshot snapshot(UUID userId, UUID jobId) {
        return JobSnapshot.of(load(userId, jobId));
    }

    @Transactional(readOnly = true)
    public Page<JobSummary> list(UUID userId, UUID sourceId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        Page<JobPosting> result = sourceId == null
                ? repository.findByUserIdOrderByFoundAtDesc(userId, pageable)
                : repository.findByUserIdAndWatchedSourceIdOrderByFoundAtDesc(userId, sourceId, pageable);
        return result.map(JobSummary::of);
    }

    @Transactional(readOnly = true)
    public long count(UUID userId) {
        return repository.countByUserId(userId);
    }

    private JobPosting load(UUID userId, UUID jobId) {
        return repository.findByIdAndUserId(jobId, userId).orElseThrow(() -> new NotFoundException("Vaga"));
    }

    private Optional<JobPosting> findDuplicate(UUID userId, JobData data) {
        if (data.urlHash() != null) {
            Optional<JobPosting> byUrl = repository.findByUserIdAndUrlHash(userId, data.urlHash());
            if (byUrl.isPresent()) {
                return byUrl;
            }
        }
        String location = TextNormalizer.normalize(data.location());
        List<JobPosting> sameRole = repository.findByUserIdAndCompanyNormAndTitleNorm(userId, data.companyNorm(), data.titleNorm());
        return sameRole.stream()
                .filter(job -> TextNormalizer.normalize(job.getLocation()).equals(location))
                // Duas URLs canônicas diferentes da mesma empresa e cargo são vagas distintas (ex.: duas cidades).
                .filter(job -> job.getCanonicalUrl() == null || data.canonicalUrl() == null
                        || Objects.equals(job.getCanonicalUrl(), data.canonicalUrl()))
                .findFirst();
    }

    private static JobData toData(JobRequest request) {
        if (request.salaryMin() != null && request.salaryMax() != null && request.salaryMin().compareTo(request.salaryMax()) > 0) {
            throw DomainException.badRequest("invalid_salary_range", "O salário mínimo é maior que o máximo");
        }
        String canonical = UrlCanonicalizer.optional(request.url());
        String company = request.company().strip();
        String title = request.title().strip();
        List<JobRequirement> requirements = request.requirements() == null ? List.of() : request.requirements().stream()
                .map(r -> new JobRequirement(r.kind(), r.text().strip(),
                        r.skill() == null || r.skill().isBlank() ? null : SkillNormalizer.normalize(r.skill()), r.minYears()))
                .toList();
        List<JobLanguage> languages = request.languages() == null ? List.of() : request.languages().stream()
                .map(l -> new JobLanguage(l.code(), l.minLevel(), l.required())).toList();
        boolean hasSalary = request.salaryMin() != null || request.salaryMax() != null;
        return new JobData(request.source(), request.url() == null || request.url().isBlank() ? null : request.url().strip(),
                canonical, canonical == null ? null : UrlCanonicalizer.hash(canonical),
                company, TextNormalizer.normalizeCompany(company), title, TextNormalizer.normalize(title),
                blankToNull(request.location()), request.country(), request.workMode(), request.seniority(),
                blankToNull(request.description()), request.salaryMin(), request.salaryMax(),
                hasSalary ? request.salaryCurrency() : null, hasSalary ? request.salaryPeriod() : null,
                requirements, languages);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
