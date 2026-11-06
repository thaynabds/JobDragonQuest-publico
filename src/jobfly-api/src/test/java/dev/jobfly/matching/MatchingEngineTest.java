package dev.jobfly.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.jobfly.common.domain.LanguageLevel;
import dev.jobfly.common.domain.Seniority;
import dev.jobfly.common.domain.WorkMode;
import dev.jobfly.jobs.JobRequirement.Kind;
import dev.jobfly.jobs.JobSnapshot;
import dev.jobfly.matching.MatchResult.Outcome;
import dev.jobfly.matching.MatchResult.Recommendation;
import dev.jobfly.profiles.ProfileSnapshot;

class MatchingEngineTest {

    private final MatchingEngine engine = new MatchingEngine();

    private static ProfileSnapshot javaProfile() {
        return new ProfileSnapshot(UUID.randomUUID(), "Desenvolvedora Java", Seniority.JUNIOR, 60,
                List.of("Desenvolvedora Java"), List.of("Recife"), Set.of(WorkMode.REMOTE, WorkMode.HYBRID),
                Map.of("java", skill("Java", 4, "1.5"), "spring boot", skill("Spring Boot", 3, "1"),
                        "postgresql", skill("PostgreSQL", 3, null), "git", skill("Git", 1, null)),
                Map.of("pt", LanguageLevel.NATIVE, "en", LanguageLevel.B1));
    }

    private static ProfileSnapshot.Skill skill(String name, int level, String years) {
        return new ProfileSnapshot.Skill(name, level, years == null ? null : new BigDecimal(years));
    }

    private static JobSnapshot job(WorkMode mode, String location, Seniority seniority, List<JobSnapshot.Requirement> reqs,
            List<JobSnapshot.Language> languages) {
        return new JobSnapshot(UUID.randomUUID(), "Sensedia", "Desenvolvedor Java Júnior", location, "BR", mode, seniority,
                null, reqs, languages);
    }

    private static JobSnapshot.Requirement mandatory(String text, String skill, String years) {
        return new JobSnapshot.Requirement(Kind.MANDATORY, text, skill, years == null ? null : new BigDecimal(years));
    }

    private static JobSnapshot.Requirement desired(String text, String skill) {
        return new JobSnapshot.Requirement(Kind.DESIRED, text, skill, null);
    }

    @Test
    void fullyCompatibleJobIsRecommended() {
        MatchResult result = engine.evaluate(javaProfile(), job(WorkMode.REMOTE, null, Seniority.JUNIOR,
                List.of(mandatory("Java", "java", "1"), mandatory("Spring Boot", "spring boot", null),
                        desired("PostgreSQL", "postgresql")), List.of()));

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.recommendation()).isEqualTo(Recommendation.APPLY);
        assertThat(result.blockers()).isEmpty();
        assertThat(result.strengths()).containsExactly("Domínio de Java (nível 4)");
        assertThat(result.titleAffinity()).isTrue();
    }

    @Test
    void scoreWeightsMandatoryAndDesiredAndExplainsEachRequirement() {
        MatchResult result = engine.evaluate(javaProfile(), job(WorkMode.REMOTE, null, null, List.of(
                mandatory("Java 3+ anos", "java", "3"),        // parcial: 1,5 de 3 anos
                mandatory("Kotlin", "kotlin", null),            // não atendido
                desired("Git", "git"),                          // parcial: nível básico
                desired("Boa comunicação", null)), List.of())); // não avaliado

        // obrigatórios: (0,5 + 0) / 2 = 0,25; desejáveis: 0,5 / 1 = 0,5 → 0,7×0,25 + 0,3×0,5 = 0,325
        assertThat(result.score()).isEqualTo(33);
        assertThat(result.requirements()).extracting(MatchResult.RequirementAssessment::outcome)
                .containsExactly(Outcome.PARTIAL, Outcome.UNMET, Outcome.PARTIAL, Outcome.NOT_EVALUATED);
        assertThat(result.requirements().get(0).reason()).contains("1.5 de 3 ano(s)");
        assertThat(result.warnings()).anyMatch(w -> w.contains("1 requisito(s) obrigatório(s) não atendido(s)"))
                .anyMatch(w -> w.contains("leitura humana"));
        assertThat(result.recommendation()).isEqualTo(Recommendation.SKIP);
    }

    @Test
    void highScoreDoesNotOverrideEliminatoryBlockers() {
        MatchResult result = engine.evaluate(javaProfile(), job(WorkMode.ONSITE, "São Paulo - SP", Seniority.SENIOR,
                List.of(mandatory("Java", "java", null)),
                List.of(new JobSnapshot.Language("en", LanguageLevel.C1, true))));

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.recommendation()).isEqualTo(Recommendation.SKIP);
        assertThat(result.blockers()).extracting(MatchResult.Blocker::code)
                .containsExactlyInAnyOrder("WORK_MODE", "LOCATION", "LANGUAGE", "SENIORITY");
    }

    @Test
    void hybridInProfileCityIsNotBlocked() {
        MatchResult result = engine.evaluate(javaProfile(), job(WorkMode.HYBRID, "Recife, PE", Seniority.MID,
                List.of(mandatory("Java", "java", null)), List.of(new JobSnapshot.Language("en", LanguageLevel.B2, false))));

        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).anyMatch(w -> w.contains("nível acima")).anyMatch(w -> w.contains("(desejável)"));
        assertThat(result.recommendation()).isEqualTo(Recommendation.APPLY);
    }

    @Test
    void infersSkillFromRequirementTextPreferringLongestName() {
        MatchResult result = engine.evaluate(javaProfile(), job(WorkMode.REMOTE, null, null,
                List.of(mandatory("Experiência com Spring Boot e APIs REST", null, null)), List.of()));

        assertThat(result.requirements().getFirst().skill()).isEqualTo("spring boot");
        assertThat(result.requirements().getFirst().outcome()).isEqualTo(Outcome.MET);
        assertThat(result.requirements().getFirst().reason()).startsWith("Identificado no texto");
    }

    @Test
    void doesNotInferJavaInsideJavascript() {
        MatchResult result = engine.evaluate(javaProfile(), job(WorkMode.REMOTE, null, null,
                List.of(mandatory("Domínio de JavaScript", null, null)), List.of()));

        assertThat(result.requirements().getFirst().outcome()).isEqualTo(Outcome.NOT_EVALUATED);
        assertThat(result.recommendation()).isEqualTo(Recommendation.INSUFFICIENT_DATA);
        assertThat(result.score()).isNull();
    }

    @Test
    void missingYearsIsPartialNotMet() {
        MatchResult result = engine.evaluate(javaProfile(), job(WorkMode.REMOTE, null, null,
                List.of(mandatory("PostgreSQL 2 anos", "postgresql", "2")), List.of()));

        assertThat(result.requirements().getFirst().outcome()).isEqualTo(Outcome.PARTIAL);
        assertThat(result.requirements().getFirst().reason()).contains("não informa o tempo");
    }

    @Test
    void titleAffinityToleratesGenderAndExtraWords() {
        ProfileSnapshot profile = new ProfileSnapshot(UUID.randomUUID(), "Estágio", Seniority.INTERN, 60,
                List.of("Estagiária de Desenvolvimento"), List.of(), Set.of(), Map.of(), Map.of());
        JobSnapshot internship = new JobSnapshot(UUID.randomUUID(), "SiDi", "Estagiário em Desenvolvimento de Software",
                null, null, null, null, null, List.of(), List.of());
        JobSnapshot sales = new JobSnapshot(UUID.randomUUID(), "X", "Estagiário de Vendas", null, null, null, null, null,
                List.of(), List.of());

        JobSnapshot withoutPreposition = new JobSnapshot(UUID.randomUUID(), "QCA", "Estagiário Desenvolvimento", null, null,
                null, null, null, List.of(), List.of());

        assertThat(MatchingEngine.titleAffinity(profile, internship)).isTrue();
        assertThat(MatchingEngine.titleAffinity(profile, withoutPreposition)).isTrue();
        assertThat(MatchingEngine.titleAffinity(profile, sales)).isFalse();
    }

    /** Issue #32: grafias como C#, .NET e JS no texto do requisito casam com as competências do perfil. */
    @Test
    void recognizesAliasesInRequirementText() {
        ProfileSnapshot dotnet = new ProfileSnapshot(UUID.randomUUID(), ".NET", Seniority.JUNIOR, 60, List.of(), List.of(),
                Set.of(), Map.of("csharp", skill("C#", 3, null), "dotnet", skill(".NET", 3, null), "javascript", skill("JS", 3, null)),
                Map.of());
        MatchResult result = engine.evaluate(dotnet, job(null, null, null, List.of(
                mandatory("C# avançado", null, null), mandatory("Experiência com .NET Core", null, null),
                mandatory("JS moderno (ES2020+)", null, null)), List.of()));

        assertThat(result.requirements()).extracting(MatchResult.RequirementAssessment::skill)
                .containsExactly("csharp", "dotnet", "javascript");
        assertThat(result.requirements()).allMatch(r -> r.outcome() == Outcome.MET);
        assertThat(result.recommendation()).isEqualTo(Recommendation.APPLY);
    }

    /** Issue #32: obrigatório parcial ou sem avaliação automática nunca vira APPLY, por maior que seja a nota. */
    @Test
    void partialOrUnevaluatedMandatoryIsReviewNotApply() {
        MatchResult partial = engine.evaluate(javaProfile(), job(WorkMode.REMOTE, null, null, List.of(
                mandatory("Java", "java", null), mandatory("Spring Boot 5+ anos", "spring boot", "5")), List.of()));
        assertThat(partial.score()).isEqualTo(75);
        assertThat(partial.recommendation()).isEqualTo(Recommendation.REVIEW);
        assertThat(partial.warnings()).anyMatch(w -> w.contains("parcialmente atendido"));

        MatchResult unevaluated = engine.evaluate(javaProfile(), job(WorkMode.REMOTE, null, null, List.of(
                mandatory("Java", "java", null), mandatory("Boa comunicação", null, null)), List.of()));
        assertThat(unevaluated.score()).isEqualTo(100);
        assertThat(unevaluated.recommendation()).isEqualTo(Recommendation.REVIEW);
    }

    /** Issue #32: a cidade é comparada por trecho, não por substring. */
    @Test
    void locationComparesCitySegmentsNotSubstrings() {
        ProfileSnapshot saoPaulo = new ProfileSnapshot(UUID.randomUUID(), "SP", Seniority.JUNIOR, 60, List.of(), List.of("São Paulo"),
                Set.of(WorkMode.ONSITE), Map.of("java", skill("Java", 4, null)), Map.of());
        List<JobSnapshot.Requirement> java = List.of(mandatory("Java", "java", null));

        assertThat(engine.evaluate(saoPaulo, job(WorkMode.ONSITE, "São Paulo do Potengi - RN", null, java, List.of())).blockers())
                .extracting(MatchResult.Blocker::code).containsExactly("LOCATION");
        assertThat(engine.evaluate(saoPaulo, job(WorkMode.ONSITE, "São Paulo, SP", null, java, List.of())).blockers()).isEmpty();
        assertThat(MatchingEngine.segments("Recife - PE (Brasil)")).containsExactly("recife", "pe", "brasil");
    }

    @Test
    void reviewWhenScoreIsSlightlyBelowMinimumOrMandatoryIsMissing() {
        assertThat(MatchingEngine.recommend(50, List.of(), 0, 60)).isEqualTo(Recommendation.REVIEW);
        assertThat(MatchingEngine.recommend(80, List.of(), 1, 60)).isEqualTo(Recommendation.REVIEW);
        assertThat(MatchingEngine.recommend(40, List.of(), 0, 60)).isEqualTo(Recommendation.SKIP);
    }

    @Test
    void rankPutsUnblockedHigherScoreFirst() {
        ProfileSnapshot java = javaProfile();
        ProfileSnapshot python = new ProfileSnapshot(UUID.randomUUID(), "Desenvolvedora Python", Seniority.JUNIOR, 60,
                List.of("Desenvolvedora Python"), List.of(), Set.of(WorkMode.REMOTE),
                Map.of("python", skill("Python", 4, "2")), Map.of());
        ProfileSnapshot onsiteOnly = new ProfileSnapshot(UUID.randomUUID(), "Presencial", Seniority.JUNIOR, 60,
                List.of(), List.of(), Set.of(WorkMode.ONSITE), Map.of("java", skill("Java", 5, "5")), Map.of());
        JobSnapshot remoteJava = job(WorkMode.REMOTE, null, Seniority.JUNIOR, List.of(mandatory("Java", "java", null)), List.of());

        List<MatchResult> ranked = engine.rank(List.of(python, onsiteOnly, java), remoteJava);

        assertThat(ranked).extracting(MatchResult::profileName)
                .containsExactly("Desenvolvedora Java", "Desenvolvedora Python", "Presencial");
        assertThat(ranked.getFirst().profileId()).isEqualTo(java.id());
    }
}
