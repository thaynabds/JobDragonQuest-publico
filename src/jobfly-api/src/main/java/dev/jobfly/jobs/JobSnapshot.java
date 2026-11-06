package dev.jobfly.jobs;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import dev.jobfly.common.domain.LanguageLevel;
import dev.jobfly.common.domain.Seniority;
import dev.jobfly.common.domain.WorkMode;

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
        List<Language> languages) {

    public record Requirement(JobRequirement.Kind kind, String text, String skill, BigDecimal minYears) {
    }

    public record Language(String code, LanguageLevel minLevel, boolean required) {
    }

    static JobSnapshot of(JobPosting job) {
        return new JobSnapshot(job.getId(), job.getCompany(), job.getTitle(), job.getLocation(), job.getCountry(),
                job.getWorkMode(), job.getSeniority(), job.getCanonicalUrl(),
                job.getRequirements().stream()
                        .map(r -> new Requirement(r.getKind(), r.getText(), r.getSkill(), r.getMinYears())).toList(),
                job.getLanguages().stream().map(l -> new Language(l.getCode(), l.getMinLevel(), l.isRequired())).toList());
    }
}
