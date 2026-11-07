package dev.jobdragonquest.matching;

import java.util.List;
import java.util.UUID;

import dev.jobdragonquest.jobs.JobRequirement;

/**
 * Resultado explicável da análise entre UM perfil e UMA vaga.
 *
 * @param score nota de 0 a 100, ou null quando a vaga não traz requisitos avaliáveis
 * @param blockers impedimentos eliminatórios, avaliados à parte da nota (RN-02)
 */
public record MatchResult(
        UUID profileId,
        String profileName,
        Integer score,
        Recommendation recommendation,
        List<RequirementAssessment> requirements,
        List<Blocker> blockers,
        List<String> strengths,
        List<String> warnings,
        boolean titleAffinity,
        String summary) {

    public enum Recommendation {
        /** Sem impedimentos, nota no mínimo do perfil e todos os obrigatórios atendidos. */
        APPLY,
        /** Vale a candidatura, mas exige revisão humana (obrigatório parcial ou nota pouco abaixo do mínimo). */
        REVIEW,
        /** Impedimento eliminatório ou nota muito abaixo do mínimo. */
        SKIP,
        /** A vaga não tem requisitos suficientes para avaliar. */
        INSUFFICIENT_DATA
    }

    public enum Outcome {
        MET,
        PARTIAL,
        UNMET,
        NOT_EVALUATED
    }

    public record RequirementAssessment(JobRequirement.Kind kind, String text, String skill, Outcome outcome, String reason) {
    }

    public record Blocker(String code, String message) {
    }
}
