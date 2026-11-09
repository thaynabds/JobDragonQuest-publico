package dev.jobdragonquest.contests;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contratos da API de concursos. */
public final class ContestDtos {

    private ContestDtos() {
    }

    public enum StageStatus { PLANNED, OPEN, DONE, MISSED, NOT_APPLICABLE }

    public record Stage(
            @NotBlank @Size(max = 160) String title,
            LocalDate date,
            LocalDate deadline,
            @NotNull StageStatus status,
            @Size(max = 2048) String url,
            @Size(max = 1000) String note) {
    }

    public record ContestRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String organ,
            @Size(max = 120) String board,
            @Size(max = 120) String notice,
            @Size(max = 200) String roleName,
            @Pattern(regexp = "[A-Z]{2}") String stateCode,
            @Size(max = 120) String city,
            @Size(min = 4, max = 40) String registration,
            @Size(max = 160) @Pattern(regexp = "\\S+(\\s+\\S+)+", message = "Informe nome e sobrenome") String searchName,
            LocalDate validUntil,
            @Size(max = 4000) String notes,
            @Size(max = 60) List<@Valid @NotNull Stage> stages) {
    }

    public record WatchRequest(@NotBlank @Size(max = 2048) String url, @Size(max = 120) String label) {
    }

    public record WatchView(UUID id, String url, String label, String status, Instant lastCheckedAt) {
    }

    public record DocumentView(String url, String status, Instant readAt) {
    }

    public record MatchView(String url, String kind, String confidence, String line, Instant foundAt) {
    }

    public record ContestResponse(UUID id, String name, String organ, String board, String notice, String roleName,
            String stateCode, String city, String registration, String searchName, LocalDate validUntil, String notes,
            List<Stage> stages, List<WatchView> watches, List<DocumentView> documents, List<MatchView> matches) {
    }

    public record ScanResult(int pages, int changed, int documents, int matches, int unread) {
    }
}
