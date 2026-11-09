package dev.jobdragonquest.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.profiles.ProfileEntry;

/** Issue #172: currículo inteiro vira itens separados para a pessoa conferir. */
class ResumeParserTest {

    private static final String CV = """
            Maria Exemplo
            maria@exemplo.dev
            R E S U M O   P R O F I S S I O N A L
            Pessoa desenvolvedora Java.
            EXPERIÊNCIA PROFISSIONAL
            💼 Estágio em Sistemas
            Nov/2026 – Nov/2027 (em andamento)
            Prefeitura Exemplo | Recife-PE
            Apoio técnico no laboratório.
            Desenvolvimento Java Júnior
            Empresa X | Recife | Jan/2024 – Dez/2025
            Criei APIs REST.
            FORMAÇÃO ACADÊMICA
            Análise e Desenvolvimento de Sistemas
            Faculdade Exemplo | Recife-PE Brasil | Ago/2025 – Dez/2027
            CURSOS E CERTIFICAÇÕES
            Bootcamp Java
            DIO | Ago/2026 – Set/2026 (53h)
            PROJETOS
            ☕ API de Orçamento Set–Out/2026
            Java 21 e Spring Boot.
            IDIOMAS
            Português nativo
            """;

    @Test
    void separatesEachSectionIntoItems() {
        var proposal = ResumeParser.parse(CV);
        assertThat(proposal.email()).isEqualTo("maria@exemplo.dev");
        assertThat(proposal.summary()).contains("Java");
        assertThat(proposal.languages()).containsExactly("Português nativo");
        assertThat(proposal.items()).extracting(ResumeParser.Item::kind).contains(ProfileEntry.Kind.EXPERIENCE, ProfileEntry.Kind.EDUCATION,
                ProfileEntry.Kind.COURSE, ProfileEntry.Kind.PROJECT);
        var job = proposal.items().stream().filter(i -> i.title().equals("Desenvolvimento Java Júnior")).findFirst().orElseThrow();
        assertThat(job.organization()).isEqualTo("Empresa X");
        assertThat(job.startDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(job.endDate()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(job.description()).isEqualTo("Criei APIs REST.");
        var project = proposal.items().stream().filter(i -> i.kind() == ProfileEntry.Kind.PROJECT).findFirst().orElseThrow();
        assertThat(project.title()).isEqualTo("API de Orçamento");
        assertThat(project.startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void certificateIsNotAResume() {
        assertThat(ResumeParser.parse("Certificamos que Maria concluiu o curso de Java, 40h, em 10/10/2026.")).isNull();
        assertThat(ResumeParser.parse(null)).isNull();
    }
}
