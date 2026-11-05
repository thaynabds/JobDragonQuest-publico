package dev.jobfly.profiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.jobfly.common.domain.LanguageLevel;
import dev.jobfly.common.domain.Seniority;
import dev.jobfly.common.domain.WorkMode;

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
        Map<String, LanguageLevel> languages) {

    public record Skill(String name, int level, BigDecimal years) {
    }

    static ProfileSnapshot of(Profile profile) {
        Map<String, Skill> skills = profile.getSkills().stream().collect(Collectors.toUnmodifiableMap(
                ProfileSkill::getNormalized, s -> new Skill(s.getName(), s.getLevel(), s.getYears()), (a, b) -> a));
        Map<String, LanguageLevel> languages = profile.getLanguages().stream().collect(Collectors.toUnmodifiableMap(
                ProfileLanguage::getCode, ProfileLanguage::getLevel, (a, b) -> a));
        return new ProfileSnapshot(profile.getId(), profile.getName(), profile.getSeniority(), profile.getMinMatchScore(),
                profile.getDesiredTitles(), profile.getLocations(), profile.getWorkModes(), skills, languages);
    }
}
