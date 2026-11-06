package dev.jobfly.sources;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.jobfly.sources.SourceKind.CollectionMode;
import dev.jobfly.sources.WatchedSource.RunStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SourceDtos {

    static final int MIN_FREQUENCY = 15;
    static final int MAX_FREQUENCY = 10_080;
    static final int DEFAULT_FREQUENCY = 360;

    private SourceDtos() {
    }

    /** Uma fonte do cadastro em lote. ATS: informe o identificador do board. Outras: informe a URL a conferir. */
    public record SourceInput(
            @NotBlank @Size(max = 120) String name,
            @NotNull SourceKind kind,
            @Size(max = 100) String identifier,
            @Size(max = 2048) String url) {
    }

    /** Várias fontes de uma vez, todas ligadas aos mesmos perfis e com a mesma frequência e palavras-chave. */
    public record BulkCreateRequest(
            @NotEmpty @Size(max = 50) List<@Valid @NotNull SourceInput> sources,
            @Size(max = 30) Set<@NotNull UUID> profileIds,
            @Min(MIN_FREQUENCY) @Max(MAX_FREQUENCY) Integer frequencyMinutes,
            @Size(max = 20) List<@NotBlank @Size(max = 80) String> keywords) {
    }

    public record DuplicateSource(int index, UUID existingId, String name) {
    }

    public record BulkCreateResult(List<SourceResponse> created, List<DuplicateSource> duplicates) {
    }

    public record UpdateSourceRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 100) String identifier,
            @Size(max = 2048) String url,
            @NotNull @Min(MIN_FREQUENCY) @Max(MAX_FREQUENCY) Integer frequencyMinutes,
            @Size(max = 20) List<@NotBlank @Size(max = 80) String> keywords,
            @Size(max = 30) Set<@NotNull UUID> profileIds,
            boolean enabled,
            @NotNull Long version) {
    }

    /**
     * Edição em lote. Campos nulos não mudam. {@code setProfileIds} substitui os perfis. {@code addProfileIds} e
     * {@code removeProfileIds} acrescentam ou retiram perfis e não podem vir junto com {@code setProfileIds}.
     */
    public record BulkUpdateRequest(
            @NotEmpty @Size(max = 200) Set<@NotNull UUID> sourceIds,
            Boolean enabled,
            @Min(MIN_FREQUENCY) @Max(MAX_FREQUENCY) Integer frequencyMinutes,
            @Size(max = 30) Set<@NotNull UUID> setProfileIds,
            @Size(max = 30) Set<@NotNull UUID> addProfileIds,
            @Size(max = 30) Set<@NotNull UUID> removeProfileIds) {
    }

    public record SourceResponse(
            UUID id, String name, SourceKind kind, String kindLabel, CollectionMode collectionMode, String identifier,
            String url, String openUrl, int frequencyMinutes, boolean enabled, Set<UUID> profileIds,
            List<String> keywords, Instant nextRunAt, Instant lastRunAt, RunStatus lastStatus, String lastMessage,
            Integer lastFound, int consecutiveFailures, boolean due, Instant createdAt, long version) {
    }

    public record CatalogEntry(SourceKind kind, String label, CollectionMode mode, boolean needsIdentifier) {
    }

    public record RunResponse(UUID id, SourceRun.Trigger trigger, RunStatus status, int found, int created,
            int recommended, String message, Instant startedAt, Instant finishedAt) {

        static RunResponse of(SourceRun run) {
            return new RunResponse(run.getId(), run.getTrigger(), run.getStatus(), run.getFound(), run.getCreated(),
                    run.getRecommended(), run.getMessage(), run.getStartedAt(), run.getFinishedAt());
        }
    }
}
