package dev.jobfly.applications;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.jobfly.applications.ApplicationEnums.EventKind;
import dev.jobfly.applications.ApplicationEnums.EventSource;
import dev.jobfly.applications.ApplicationEnums.EventStatus;
import dev.jobfly.applications.ApplicationEnums.Origin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    /**
     * Cadastro de candidatura. Com {@code jobId}, empresa, cargo e URL vêm da vaga quando não forem informados.
     * Sem vaga (candidatura externa), empresa e cargo são obrigatórios.
     */
    public record CreateApplicationRequest(
            UUID jobId,
            UUID profileId,
            @NotNull Origin origin,
            @Size(max = 80) String platform,
            @Size(max = 200) String company,
            @Size(max = 200) String title,
            @Size(max = 2048) String url,
            @Pattern(regexp = "[A-Z]{2}") String country,
            Instant appliedAt,
            ApplicationState state,
            @Size(max = 4000) String notes,
            @Size(max = 500) String nextAction,
            Instant nextDueAt) {
    }

    public record UpdateApplicationRequest(
            UUID profileId,
            @Size(max = 80) String platform,
            @NotBlank @Size(max = 200) String company,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2048) String url,
            @Pattern(regexp = "[A-Z]{2}") String country,
            Instant appliedAt,
            @Size(max = 4000) String notes,
            @Size(max = 500) String nextAction,
            Instant nextDueAt,
            @NotNull Long version) {
    }

    public record TransitionRequest(
            @NotNull ApplicationState toState,
            @NotNull EventSource source,
            @NotBlank @Size(max = 4000) String evidence,
            /** Obrigatória para fontes automáticas. Para MANUAL vale sempre 1. */
            @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal confidence,
            Instant occurredAt,
            @Size(max = 500) String nextAction,
            Instant nextDueAt) {
    }

    public record NoteRequest(@NotBlank @Size(max = 4000) String text) {
    }

    public record ApplicationResponse(
            UUID id, UUID jobId, UUID profileId, Origin origin, String platform, String company, String title,
            String url, String country, Instant appliedAt, ApplicationState state, Instant stateChangedAt,
            Instant lastEvidenceAt, boolean stale, String nextAction, Instant nextDueAt, Integer matchScore,
            String notes, Instant createdAt, Instant updatedAt, long version) {
    }

    public record EventResponse(UUID id, EventKind kind, ApplicationState fromState, ApplicationState toState,
            EventSource source, String evidence, BigDecimal confidence, EventStatus status, Instant occurredAt,
            Instant createdAt) {

        static EventResponse of(ApplicationEvent event) {
            return new EventResponse(event.getId(), event.getKind(), event.getFromState(), event.getToState(),
                    event.getSource(), event.getEvidence(), event.getConfidence(), event.getStatus(),
                    event.getOccurredAt(), event.getCreatedAt());
        }
    }

    public record ApplicationDetail(ApplicationResponse application, List<EventResponse> events) {
    }

    /** {@code duplicate}: já existia (RN-06). {@code replayed}: mesma Idempotency-Key de antes. */
    public record CreateResult(ApplicationResponse application, boolean duplicate, boolean replayed) {
    }

    public enum TransitionOutcome {
        /** A etapa mudou. */
        APPLIED,
        /** Confiança baixa: virou sugestão e espera a pessoa decidir. */
        SUGGESTED,
        /** Mesma etapa: a evidência foi registrada e a etapa não mudou. */
        NOTED
    }

    public record TransitionResult(TransitionOutcome outcome, ApplicationResponse application, EventResponse event) {
    }

    public record BoardColumn(ApplicationState state, long count, List<ApplicationResponse> cards) {
    }
}
