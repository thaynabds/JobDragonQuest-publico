package dev.jobdragonquest.jobs;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.jobdragonquest.common.domain.LanguageLevel;
import dev.jobdragonquest.common.domain.Seniority;
import dev.jobdragonquest.common.domain.WorkMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class JobDtos {

    private JobDtos() {
    }

    public record JobRequest(
            /** Onde a vaga foi encontrada: LINKEDIN, GUPY, GREENHOUSE, INSTAGRAM, COMPANY_SITE, MANUAL... */
            @NotBlank @Pattern(regexp = "[A-Z0-9_]{2,40}") String source,
            @Size(max = 2048) String url,
            @NotBlank @Size(max = 200) String company,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 200) String location,
            @Pattern(regexp = "[A-Z]{2}") String country,
            WorkMode workMode,
            Seniority seniority,
            @Size(max = 20000) String description,
            @PositiveOrZero BigDecimal salaryMin,
            @PositiveOrZero BigDecimal salaryMax,
            @Pattern(regexp = "[A-Z]{3}") String salaryCurrency,
            @Pattern(regexp = "HOUR|MONTH|YEAR") String salaryPeriod,
            @Size(max = 60) List<@Valid @NotNull RequirementDto> requirements,
            @Size(max = 10) List<@Valid @NotNull LanguageRequirementDto> languages) {
    }

    public record RequirementDto(
            @NotNull JobRequirement.Kind kind,
            @NotBlank @Size(max = 500) String text,
            @Size(max = 80) String skill,
            @DecimalMin("0.0") @DecimalMax("30.0") BigDecimal minYears) {
    }

    public record LanguageRequirementDto(
            @NotBlank @Pattern(regexp = "[a-z]{2}") String code,
            @NotNull LanguageLevel minLevel,
            boolean required) {
    }

    public record StatusRequest(@NotNull JobStatus status) {
    }

    public record JobResponse(
            UUID id, String source, String url, String company, String title, String location, String country,
            WorkMode workMode, Seniority seniority, String description, BigDecimal salaryMin, BigDecimal salaryMax,
            String salaryCurrency, String salaryPeriod, JobStatus status, List<RequirementDto> requirements,
            List<LanguageRequirementDto> languages, Instant foundAt, Instant updatedAt, UUID watchedSourceId) {

        static JobResponse of(JobPosting job) {
            return new JobResponse(job.getId(), job.getSource(), job.getSourceUrl(), job.getCompany(), job.getTitle(),
                    job.getLocation(), job.getCountry(), job.getWorkMode(), job.getSeniority(), job.getDescription(),
                    job.getSalaryMin(), job.getSalaryMax(), job.getSalaryCurrency(), job.getSalaryPeriod(),
                    job.getStatus(),
                    job.getRequirements().stream()
                            .map(r -> new RequirementDto(r.getKind(), r.getText(), r.getSkill(), r.getMinYears())).toList(),
                    job.getLanguages().stream()
                            .map(l -> new LanguageRequirementDto(l.getCode(), l.getMinLevel(), l.isRequired())).toList(),
                    job.getFoundAt(), job.getUpdatedAt(), job.getWatchedSourceId());
        }
    }

    public record JobSummary(UUID id, String company, String title, String location, WorkMode workMode,
            JobStatus status, String source, String url, Instant foundAt, UUID watchedSourceId) {

        static JobSummary of(JobPosting job) {
            return new JobSummary(job.getId(), job.getCompany(), job.getTitle(), job.getLocation(), job.getWorkMode(),
                    job.getStatus(), job.getSource(), job.getSourceUrl(), job.getFoundAt(), job.getWatchedSourceId());
        }
    }

    /** Resultado do cadastro: {@code duplicate} indica que a vaga já existia e nada foi criado. */
    public record JobCreated(JobResponse job, boolean duplicate) {
    }
}
