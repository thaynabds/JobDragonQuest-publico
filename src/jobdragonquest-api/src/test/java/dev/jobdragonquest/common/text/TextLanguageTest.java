package dev.jobdragonquest.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextLanguageTest {

    @Test
    void recognizesTheLanguageOfTheJob() {
        assertThat(TextLanguage.guess("We are looking for a developer with experience in Java and the skills to join our team")).isEqualTo("en");
        assertThat(TextLanguage.guess("Buscamos uma pessoa com experiência em Java para a vaga de desenvolvimento e conhecimento em SQL")).isEqualTo("pt");
        assertThat(TextLanguage.guess("Buscamos una persona con experiencia y conocimientos en el puesto de trabajo que ofrecemos")).isEqualTo("es");
    }

    @Test
    void doesNotGuessWithoutEvidence() {
        assertThat(TextLanguage.guess(null)).isNull();
        assertThat(TextLanguage.guess("Java Spring")).isNull();
    }
}
