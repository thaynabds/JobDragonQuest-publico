package dev.jobdragonquest.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NormalizerTest {

    @Test
    void normalizesAccentsCaseAndSpaces() {
        assertThat(TextNormalizer.normalize("  Desenvolvedora   JÚNIOR – São Paulo! ")).isEqualTo("desenvolvedora junior sao paulo");
        assertThat(TextNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void companySuffixesDoNotDistinguishCompanies() {
        assertThat(TextNormalizer.normalizeCompany("Avanade Brasil Ltda")).isEqualTo("avanade brasil");
        assertThat(TextNormalizer.normalizeCompany("CI&T S.A.")).isEqualTo(TextNormalizer.normalizeCompany("CI&T"));
        assertThat(TextNormalizer.normalizeCompany("Ltda")).isEqualTo("ltda");
    }

    @ParameterizedTest
    @CsvSource({
            "JS, javascript",
            "Node.js, node.js",
            "nodejs, node.js",
            "Spring-Boot, spring boot",
            "C#, csharp",
            ".NET, dotnet",
            "Postgres, postgresql",
            "Java, java",
            "Spring, spring"})
    void unifiesSpellingsOfTheSameTechnology(String input, String expected) {
        assertThat(SkillNormalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    void neverMergesDifferentTechnologies() {
        assertThat(SkillNormalizer.normalize("Java")).isNotEqualTo(SkillNormalizer.normalize("JavaScript"));
        assertThat(SkillNormalizer.normalize("Spring")).isNotEqualTo(SkillNormalizer.normalize("Spring Boot"));
    }
}
