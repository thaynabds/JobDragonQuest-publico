package dev.jobdragonquest.profiles;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.common.domain.Seniority;

/** Idiomas pelo nome por extenso e degraus da senioridade ampla. */
class ProfileCatalogTest {

    @Test
    void resolvesLanguageNamesInSeveralLanguagesWithoutAccents() {
        assertThat(LanguageCatalog.resolve("Inglês")).contains("en");
        assertThat(LanguageCatalog.resolve("ingles")).contains("en");
        assertThat(LanguageCatalog.resolve("ENGLISH")).contains("en");
        assertThat(LanguageCatalog.resolve("Inglés")).contains("en");
        assertThat(LanguageCatalog.resolve("Português")).contains("pt");
        assertThat(LanguageCatalog.resolve("Alemão")).contains("de");
        assertThat(LanguageCatalog.resolve("francés")).contains("fr");
        assertThat(LanguageCatalog.resolve("Deutsch")).contains("de");
        assertThat(LanguageCatalog.resolve("japonês")).contains("ja");
        assertThat(LanguageCatalog.resolve("Libras")).contains("bzs");
        assertThat(LanguageCatalog.resolve("es")).contains("es");
        assertThat(LanguageCatalog.resolve("zz")).isEmpty();
        assertThat(LanguageCatalog.resolve("idioma inventado")).isEmpty();
        assertThat(LanguageCatalog.resolve(null)).isEmpty();
    }

    @Test
    void seniorityRankKeepsTheOldDistancesAndCoversNewLevels() {
        assertThat(Seniority.INTERN.gapTo(Seniority.JUNIOR)).isEqualTo(1);
        assertThat(Seniority.JUNIOR.gapTo(Seniority.SENIOR)).isEqualTo(2);
        assertThat(Seniority.SENIOR.gapTo(Seniority.LEAD)).isEqualTo(1);
        assertThat(Seniority.APPRENTICE.gapTo(Seniority.INTERN)).isZero();
        assertThat(Seniority.TRAINEE.gapTo(Seniority.JUNIOR)).isZero();
        assertThat(Seniority.ENTRY.gapTo(Seniority.MID)).isEqualTo(1);
        assertThat(Seniority.SPECIALIST.gapTo(Seniority.LEAD)).isZero();
        assertThat(Seniority.COORDINATOR.gapTo(Seniority.MANAGER)).isZero();
        assertThat(Seniority.MANAGER.gapTo(Seniority.DIRECTOR)).isEqualTo(1);
        assertThat(Seniority.DIRECTOR.gapTo(Seniority.EXECUTIVE)).isEqualTo(1);
        assertThat(Seniority.EXECUTIVE.gapTo(Seniority.JUNIOR)).isNegative();
    }

    @Test
    void decimalYearsBecomeYearsAndMonths() {
        var skill = ProfileService.toSkill(new ProfileDtos.SkillDto("Java", 3, new java.math.BigDecimal("2.99"), null, null));
        assertThat(skill.getExpYears()).isEqualTo(3);
        assertThat(skill.getExpMonths()).isZero();
        assertThat(ProfileSkill.totalYears(null, null, null)).isNull();
        assertThat(ProfileSkill.totalYears(0, 0, 200)).isEqualByComparingTo("0.5");
    }
}
