package dev.jobdragonquest.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.common.domain.Seniority;

class MultilingualTermsTest {

    @Test
    void cargoEmUmIdiomaEncontraVagaEmOutro() {
        assertThat(TitleMatcher.containsAllWords("Software Developer Intern", "Estágio")).isTrue();
        assertThat(TitleMatcher.containsAllWords("Desarrollador Java Semi Senior", "Java Pleno")).isTrue();
        assertThat(TitleMatcher.containsAllWords("Desenvolvedora Back-End Júnior", "Junior Backend Developer")).isTrue();
        assertThat(TitleMatcher.containsAllWords("Pasantía en Datos", "Data Internship")).isTrue();
    }

    @Test
    void naoMisturaTermosDiferentes() {
        assertThat(TitleMatcher.containsAllWords("Senior Java Developer", "Java Junior")).isFalse();
        assertThat(TitleMatcher.containsAllWords("Engenheiro Civil", "Software Developer")).isFalse();
    }

    @Test
    void deduzONivelDoTitulo() {
        assertThat(TitleSeniority.infer("Senior Backend Engineer")).isEqualTo(Seniority.SENIOR);
        assertThat(TitleSeniority.infer("Sênior Java")).isEqualTo(Seniority.SENIOR);
        assertThat(TitleSeniority.infer("Estágio em Ciência de Dados")).isEqualTo(Seniority.INTERN);
        assertThat(TitleSeniority.infer("Software Developer Co-op")).isEqualTo(Seniority.INTERN);
        assertThat(TitleSeniority.infer("Desarrollador Jr.")).isEqualTo(Seniority.JUNIOR);
        assertThat(TitleSeniority.infer("Engenharia Backend Pleno")).isEqualTo(Seniority.MID);
        assertThat(TitleSeniority.infer("Staff Engineer")).isEqualTo(Seniority.SPECIALIST);
        assertThat(TitleSeniority.infer("Software Engineer")).isNull();
    }
}
