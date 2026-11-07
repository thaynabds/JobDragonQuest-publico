package dev.jobdragonquest.matching;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import dev.jobdragonquest.common.domain.LanguageLevel;
import dev.jobdragonquest.common.domain.WorkMode;
import dev.jobdragonquest.common.text.SkillNormalizer;
import dev.jobdragonquest.common.text.TextNormalizer;
import dev.jobdragonquest.common.text.TitleMatcher;
import dev.jobdragonquest.jobs.JobRequirement.Kind;
import dev.jobdragonquest.jobs.JobSnapshot;
import dev.jobdragonquest.matching.MatchResult.Blocker;
import dev.jobdragonquest.matching.MatchResult.Outcome;
import dev.jobdragonquest.matching.MatchResult.Recommendation;
import dev.jobdragonquest.matching.MatchResult.RequirementAssessment;
import dev.jobdragonquest.profiles.ProfileSnapshot;

/**
 * Análise determinística e explicável de compatibilidade (ADR-005). Função pura: mesma entrada, mesmo resultado.
 *
 * <p>Nota: obrigatórios pesam 70% e desejáveis 30%. Atendido vale 1, parcial vale 0,5 e não atendido vale 0.
 * Requisitos sem competência identificável ficam fora da conta e aparecem como "não avaliado".
 * Os impedimentos (modalidade, local, idioma exigido, senioridade) não entram na nota: eles bloqueiam a recomendação.
 */
@Component
public class MatchingEngine {

    static final BigDecimal MANDATORY_WEIGHT = new BigDecimal("0.7");
    static final BigDecimal DESIRED_WEIGHT = new BigDecimal("0.3");
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Margem abaixo do mínimo do perfil em que a vaga ainda merece revisão humana. */
    static final int REVIEW_MARGIN = 15;
    private static final int BASIC_LEVEL = 1;
    private static final int STRONG_LEVEL = 4;

    public MatchResult evaluate(ProfileSnapshot profile, JobSnapshot job) {
        List<RequirementAssessment> assessments = job.requirements().stream().map(r -> assess(profile, r)).toList();
        List<Blocker> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        checkWorkMode(profile, job, blockers);
        checkLocation(profile, job, blockers);
        checkLanguages(profile, job, blockers, warnings);
        checkSeniority(profile, job, blockers, warnings);

        Integer score = score(assessments);
        List<String> strengths = strengths(profile, assessments);
        long unmetMandatory = countMandatory(assessments, Outcome.UNMET);
        long partialMandatory = countMandatory(assessments, Outcome.PARTIAL);
        long unevaluatedMandatory = countMandatory(assessments, Outcome.NOT_EVALUATED);
        if (unmetMandatory > 0) {
            warnings.add(unmetMandatory + " requisito(s) obrigatório(s) não atendido(s)");
        }
        if (partialMandatory > 0) {
            warnings.add(partialMandatory + " requisito(s) obrigatório(s) só parcialmente atendido(s)");
        }
        long notEvaluated = assessments.stream().filter(a -> a.outcome() == Outcome.NOT_EVALUATED).count();
        if (notEvaluated > 0) {
            warnings.add(notEvaluated + " requisito(s) precisam de leitura humana (sem competência identificável)");
        }
        // Só recomenda candidatura direta com todos os obrigatórios atendidos e avaliados (RN-02).
        long mandatoryIssues = unmetMandatory + partialMandatory + unevaluatedMandatory;
        Recommendation recommendation = recommend(score, blockers, mandatoryIssues, profile.minMatchScore());
        boolean affinity = titleAffinity(profile, job);
        return new MatchResult(profile.id(), profile.name(), score, recommendation, assessments, List.copyOf(blockers),
                strengths, List.copyOf(warnings), affinity, summary(score, recommendation, blockers, profile.minMatchScore()));
    }

    /** Avalia todos os perfis e devolve do mais para o menos adequado (RF-04.3). */
    public List<MatchResult> rank(List<ProfileSnapshot> profiles, JobSnapshot job) {
        return profiles.stream()
                .map(profile -> evaluate(profile, job))
                .sorted(Comparator
                        .comparing((MatchResult r) -> !r.blockers().isEmpty())
                        .thenComparing(r -> r.score() == null ? -1 : r.score(), Comparator.reverseOrder())
                        .thenComparing(r -> !r.titleAffinity())
                        .thenComparing(MatchResult::profileName))
                .toList();
    }

    private RequirementAssessment assess(ProfileSnapshot profile, JobSnapshot.Requirement requirement) {
        String skill = requirement.skill();
        boolean inferred = false;
        if (skill == null) {
            Optional<String> found = inferSkill(profile.skills(), requirement.text());
            if (found.isEmpty()) {
                return new RequirementAssessment(requirement.kind(), requirement.text(), null, Outcome.NOT_EVALUATED,
                        "Não foi possível relacionar este requisito a uma competência do perfil");
            }
            skill = found.get();
            inferred = true;
        }
        ProfileSnapshot.Skill owned = profile.skills().get(skill);
        if (owned == null) {
            return new RequirementAssessment(requirement.kind(), requirement.text(), skill, Outcome.UNMET,
                    "O perfil não declara " + skill);
        }
        String prefix = inferred ? "Identificado no texto: " : "";
        BigDecimal minYears = requirement.minYears();
        if (minYears != null && owned.years() == null) {
            return new RequirementAssessment(requirement.kind(), requirement.text(), skill, Outcome.PARTIAL,
                    prefix + "pede " + minYears.stripTrailingZeros().toPlainString() + " ano(s), e o perfil não informa o tempo de uso");
        }
        if (minYears != null && owned.years().compareTo(minYears) < 0) {
            return new RequirementAssessment(requirement.kind(), requirement.text(), skill, Outcome.PARTIAL,
                    prefix + owned.years().stripTrailingZeros().toPlainString() + " de "
                            + minYears.stripTrailingZeros().toPlainString() + " ano(s) exigidos");
        }
        if (owned.level() <= BASIC_LEVEL) {
            return new RequirementAssessment(requirement.kind(), requirement.text(), skill, Outcome.PARTIAL,
                    prefix + "o perfil declara nível básico em " + owned.name());
        }
        return new RequirementAssessment(requirement.kind(), requirement.text(), skill, Outcome.MET,
                prefix + owned.name() + " nível " + owned.level());
    }

    /**
     * Procura no texto do requisito, como palavra inteira, uma competência que o perfil declara, em qualquer grafia:
     * a canônica, os apelidos ("C#" para csharp) e o nome como a pessoa escreveu.
     */
    private static Optional<String> inferSkill(Map<String, ProfileSnapshot.Skill> skills, String text) {
        String normalized = " " + TextNormalizer.normalize(text) + " ";
        return skills.entrySet().stream()
                .filter(entry -> spellings(entry.getKey(), entry.getValue()).stream()
                        .anyMatch(form -> Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(form) + "(?![\\p{L}\\p{N}+#])")
                                .matcher(normalized).find()))
                .map(Map.Entry::getKey)
                // Prefere o nome mais longo: em "spring boot" ganha "spring boot", não "spring".
                .max(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
    }

    private static Set<String> spellings(String canonical, ProfileSnapshot.Skill skill) {
        Set<String> forms = new LinkedHashSet<>(SkillNormalizer.surfaceForms(canonical));
        forms.add(TextNormalizer.normalize(skill.name()));
        forms.removeIf(form -> form.length() < 2);
        return forms;
    }

    /** Aritmética decimal exata: 0,325 vira 33, e não 32 como daria em ponto flutuante. */
    private static Integer score(List<RequirementAssessment> assessments) {
        Optional<BigDecimal> mandatory = ratio(assessments, Kind.MANDATORY);
        Optional<BigDecimal> desired = ratio(assessments, Kind.DESIRED);
        if (mandatory.isEmpty() && desired.isEmpty()) {
            return null;
        }
        BigDecimal value;
        if (mandatory.isPresent() && desired.isPresent()) {
            value = MANDATORY_WEIGHT.multiply(mandatory.get()).add(DESIRED_WEIGHT.multiply(desired.get()));
        } else {
            value = mandatory.orElseGet(desired::get);
        }
        return value.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private static Optional<BigDecimal> ratio(List<RequirementAssessment> assessments, Kind kind) {
        List<RequirementAssessment> evaluated = assessments.stream()
                .filter(a -> a.kind() == kind && a.outcome() != Outcome.NOT_EVALUATED).toList();
        if (evaluated.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal points = evaluated.stream().map(a -> switch (a.outcome()) {
            case MET -> BigDecimal.ONE;
            case PARTIAL -> HALF;
            default -> BigDecimal.ZERO;
        }).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(points.divide(BigDecimal.valueOf(evaluated.size()), 10, RoundingMode.HALF_UP));
    }

    private static void checkWorkMode(ProfileSnapshot profile, JobSnapshot job, List<Blocker> blockers) {
        if (job.workMode() != null && !profile.workModes().isEmpty() && !profile.workModes().contains(job.workMode())) {
            blockers.add(new Blocker("WORK_MODE", "A vaga é " + job.workMode() + " e o perfil aceita " + profile.workModes()));
        }
    }

    private static void checkLocation(ProfileSnapshot profile, JobSnapshot job, List<Blocker> blockers) {
        boolean presential = job.workMode() == WorkMode.ONSITE || job.workMode() == WorkMode.HYBRID;
        if (!presential || job.location() == null || profile.locations().isEmpty()) {
            return;
        }
        // Compara a cidade (primeiro trecho do local do perfil) com os trechos do local da vaga, sem substring:
        // "São Paulo" não casa com "São Paulo do Potengi - RN", mas "Recife" casa com "Recife, PE".
        List<String> jobSegments = segments(job.location());
        boolean matches = profile.locations().stream()
                .map(location -> segments(location))
                .filter(parts -> !parts.isEmpty())
                .anyMatch(parts -> jobSegments.contains(parts.getFirst()));
        if (!matches) {
            blockers.add(new Blocker("LOCATION", "Presença exigida em " + job.location() + ", fora das localidades do perfil"));
        }
    }

    static List<String> segments(String location) {
        return Arrays.stream(location.split("[,;/|()\\-–]+"))
                .map(TextNormalizer::normalize)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private static void checkLanguages(ProfileSnapshot profile, JobSnapshot job, List<Blocker> blockers, List<String> warnings) {
        for (JobSnapshot.Language language : job.languages()) {
            LanguageLevel owned = profile.languages().get(language.code());
            if (owned != null && owned.satisfies(language.minLevel())) {
                continue;
            }
            String have = owned == null ? "não declarado" : owned.name();
            String message = "Idioma " + language.code() + ": pede " + language.minLevel() + ", perfil tem " + have;
            if (language.required()) {
                blockers.add(new Blocker("LANGUAGE", message));
            } else {
                warnings.add(message + " (desejável)");
            }
        }
    }

    private static void checkSeniority(ProfileSnapshot profile, JobSnapshot job, List<Blocker> blockers, List<String> warnings) {
        if (job.seniority() == null || profile.seniority() == null) {
            return;
        }
        int gap = profile.seniority().gapTo(job.seniority());
        if (gap >= 2) {
            blockers.add(new Blocker("SENIORITY", "A vaga é " + job.seniority() + " e o perfil é " + profile.seniority()));
        } else if (gap == 1) {
            warnings.add("A vaga pede um nível acima (" + job.seniority() + ") do perfil (" + profile.seniority() + ")");
        }
    }

    private static List<String> strengths(ProfileSnapshot profile, List<RequirementAssessment> assessments) {
        return assessments.stream()
                .filter(a -> a.outcome() == Outcome.MET)
                .map(a -> profile.skills().get(a.skill()))
                .filter(skill -> skill != null && skill.level() >= STRONG_LEVEL)
                .map(skill -> "Domínio de " + skill.name() + " (nível " + skill.level() + ")")
                .distinct()
                .toList();
    }

    private static long countMandatory(List<RequirementAssessment> assessments, Outcome outcome) {
        return assessments.stream().filter(a -> a.kind() == Kind.MANDATORY && a.outcome() == outcome).count();
    }

    /** {@code mandatoryIssues}: obrigatórios não atendidos, parciais ou sem avaliação automática. */
    static Recommendation recommend(Integer score, List<Blocker> blockers, long mandatoryIssues, int minScore) {
        if (!blockers.isEmpty()) {
            return Recommendation.SKIP;
        }
        if (score == null) {
            return Recommendation.INSUFFICIENT_DATA;
        }
        if (score >= minScore && mandatoryIssues == 0) {
            return Recommendation.APPLY;
        }
        if (score >= minScore - REVIEW_MARGIN) {
            return Recommendation.REVIEW;
        }
        return Recommendation.SKIP;
    }

    /** O cargo da vaga contém todas as palavras de algum cargo desejado (comparação por radical: {@link TitleMatcher}). */
    static boolean titleAffinity(ProfileSnapshot profile, JobSnapshot job) {
        return profile.desiredTitles().stream().anyMatch(desired -> TitleMatcher.containsAllWords(job.title(), desired));
    }

    private static String summary(Integer score, Recommendation recommendation, List<Blocker> blockers, int minScore) {
        return switch (recommendation) {
            case APPLY -> "Compatível: nota " + score + " (mínimo " + minScore + "), sem impedimentos.";
            case REVIEW -> "Revisar antes de candidatar: nota " + score + " (mínimo " + minScore + ").";
            case SKIP -> blockers.isEmpty()
                    ? "Pouco compatível: nota " + score + " (mínimo " + minScore + ")."
                    : "Não recomendada: " + blockers.size() + " impedimento(s) eliminatório(s).";
            case INSUFFICIENT_DATA -> "A vaga não traz requisitos suficientes para uma análise automática.";
        };
    }
}
