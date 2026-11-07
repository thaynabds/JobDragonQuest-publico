package dev.jobdragonquest.jobs;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import dev.jobdragonquest.common.domain.LanguageLevel;
import dev.jobdragonquest.common.domain.Seniority;
import dev.jobdragonquest.common.domain.WorkMode;

/** Visão imutável da vaga para a análise de compatibilidade e para outros módulos. */
public record JobSnapshot(
        UUID id,
        String company,
        String title,
        String location,
        String country,
        WorkMode workMode,
        Seniority seniority,
        String canonicalUrl,
        List<Requirement> requirements,
        List<Language> languages,
        String description,
        Salary salary) {

    public record Requirement(JobRequirement.Kind kind, String text, String skill, BigDecimal minYears) {
    }

    public record Language(String code, LanguageLevel minLevel, boolean required) {
    }

    /** Faixa salarial anunciada; {@code period} é HOUR, MONTH ou YEAR (ou nulo, quando a vaga não diz). */
    public record Salary(BigDecimal min, BigDecimal max, String currency, String period) {
    }

    /** Vaga sem descrição nem salário (usado onde só os requisitos importam). */
    public JobSnapshot(UUID id, String company, String title, String location, String country, WorkMode workMode,
            Seniority seniority, String canonicalUrl, List<Requirement> requirements, List<Language> languages) {
        this(id, company, title, location, country, workMode, seniority, canonicalUrl, requirements, languages, null, null);
    }

    static JobSnapshot of(JobPosting job) {
        Salary salary = job.getSalaryMin() == null && job.getSalaryMax() == null ? null
                : new Salary(job.getSalaryMin(), job.getSalaryMax(), job.getSalaryCurrency(), job.getSalaryPeriod());
        return new JobSnapshot(job.getId(), job.getCompany(), job.getTitle(), job.getLocation(), job.getCountry(),
                job.getWorkMode(), job.getSeniority(), job.getCanonicalUrl(),
                job.getRequirements().stream()
                        .map(r -> new Requirement(r.getKind(), r.getText(), r.getSkill(), r.getMinYears())).toList(),
                job.getLanguages().stream().map(l -> new Language(l.getCode(), l.getMinLevel(), l.isRequired())).toList(),
                job.getDescription(), salary);
    }
}
