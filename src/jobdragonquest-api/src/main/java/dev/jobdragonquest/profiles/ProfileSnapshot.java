package dev.jobdragonquest.profiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.jobdragonquest.common.domain.LanguageLevel;
import dev.jobdragonquest.common.domain.Seniority;
import dev.jobdragonquest.common.domain.WorkMode;
import dev.jobdragonquest.profiles.ProfileEnums.AutomationMode;
import dev.jobdragonquest.profiles.ProfileEnums.EmploymentType;
import dev.jobdragonquest.profiles.ProfileEnums.SalaryPeriod;

/**
 * Visão imutável de um perfil, entregue a outros módulos (compatibilidade, documentos, agentes).
 * Contém só os dados de UM perfil, e por isso a análise não mistura perfis (RF-02.4).
 */
public record ProfileSnapshot(
        UUID id,
        String name,
        Seniority seniority,
        int minMatchScore,
        List<String> desiredTitles,
        List<String> locations,
        Set<WorkMode> workModes,
        Map<String, Skill> skills,
        Map<String, LanguageLevel> languages,
        Filters filters,
        AutomationMode automationMode) {

    public record Skill(String name, int level, BigDecimal years) {
    }

    /**
     * Preferências que filtram vagas: empresas e palavras excluídas, palavras obrigatórias, tipos de contrato aceitos,
     * países onde pode trabalhar e salário pretendido. Lista vazia ou valor nulo quer dizer "sem filtro".
     */
    public record Filters(
            List<String> excludedCompanies,
            List<String> includeKeywords,
            List<String> excludeKeywords,
            Set<EmploymentType> employmentTypes,
            Set<String> workAuthorization,
            BigDecimal salaryExpectation,
            String salaryCurrency,
            SalaryPeriod salaryPeriod) {

        public static final Filters NONE = new Filters(List.of(), List.of(), List.of(), Set.of(), Set.of(), null, null, null);
    }

    /** Perfil sem filtros de preferência (usado onde só a compatibilidade técnica importa). */
    public ProfileSnapshot(UUID id, String name, Seniority seniority, int minMatchScore, List<String> desiredTitles,
            List<String> locations, Set<WorkMode> workModes, Map<String, Skill> skills, Map<String, LanguageLevel> languages) {
        this(id, name, seniority, minMatchScore, desiredTitles, locations, workModes, skills, languages, Filters.NONE);
    }

    /** Perfil com filtros e modo de automação padrão (fila de revisão). */
    public ProfileSnapshot(UUID id, String name, Seniority seniority, int minMatchScore, List<String> desiredTitles,
            List<String> locations, Set<WorkMode> workModes, Map<String, Skill> skills, Map<String, LanguageLevel> languages,
            Filters filters) {
        this(id, name, seniority, minMatchScore, desiredTitles, locations, workModes, skills, languages, filters, AutomationMode.REVIEW);
    }

    public ProfileSnapshot {
        filters = filters == null ? Filters.NONE : filters;
        automationMode = automationMode == null ? AutomationMode.REVIEW : automationMode;
    }

    static ProfileSnapshot of(Profile profile) {
        Map<String, Skill> skills = profile.getSkills().stream().collect(Collectors.toUnmodifiableMap(
                ProfileSkill::getNormalized, s -> new Skill(s.getName(), s.getLevel(), s.getYears()), (a, b) -> a));
        Map<String, LanguageLevel> languages = profile.getLanguages().stream().collect(Collectors.toUnmodifiableMap(
                ProfileLanguage::getCode, ProfileLanguage::getLevel, (a, b) -> a));
        Profile.ProfileDetails details = profile.getDetails();
        Filters filters = new Filters(details.excludedCompanies(), details.includeKeywords(), details.excludeKeywords(),
                details.employmentTypes(),
                details.workAuthorization().stream().map(code -> code.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet()),
                profile.getSalaryExpectation(), profile.getSalaryCurrency(), details.salaryPeriod());
        return new ProfileSnapshot(profile.getId(), profile.getName(), profile.getSeniority(), profile.getMinMatchScore(),
                profile.getDesiredTitles(), profile.getLocations(), profile.getWorkModes(), skills, languages, filters,
                profile.getAutomationMode());
    }
}
