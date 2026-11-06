package dev.jobfly.profiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.jobfly.common.domain.LanguageLevel;
import dev.jobfly.common.domain.Seniority;
import dev.jobfly.common.domain.WorkMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record ProfileRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 4000) String objective,
            @NotNull Seniority seniority,
            @Min(0) @Max(100) Integer minMatchScore,
            @PositiveOrZero @DecimalMax("9999999999.99") BigDecimal salaryExpectation,
            @Pattern(regexp = "[A-Z]{3}") String salaryCurrency,
            @Size(max = 500) String availability,
            @Size(max = 2000) String restrictions,
            @Pattern(regexp = "pt-BR|en") String preferredLanguage,
            @Size(max = 10) List<@NotBlank @Size(max = 160) String> desiredTitles,
            @Size(max = 20) List<@NotBlank @Size(max = 160) String> locations,
            @Size(max = 3) Set<@NotNull WorkMode> workModes,
            @Size(max = 100) List<@Valid @NotNull SkillDto> skills,
            @Size(max = 10) List<@Valid @NotNull LanguageDto> languages,
            @Size(max = 100) List<@Valid @NotNull EntryDto> entries,
            /** Obrigatória na atualização: protege contra sobrescrever a edição de outra aba (lock otimista). */
            Long version) {
    }

    public record SkillDto(
            @NotBlank @Size(max = 80) String name,
            @Min(1) @Max(5) int level,
            @DecimalMin("0.0") @DecimalMax("60.0") BigDecimal years) {
    }

    public record LanguageDto(
            @NotBlank @Pattern(regexp = "[a-z]{2}") String code,
            @NotNull LanguageLevel level) {
    }

    public record EntryDto(
            @NotNull ProfileEntry.Kind kind,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 200) String organization,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 4000) String description,
            @Size(max = 2048) String url) {
    }

    public record ProfileResponse(
            UUID id, String name, String objective, Seniority seniority, int minMatchScore,
            BigDecimal salaryExpectation, String salaryCurrency, String availability, String restrictions,
            String preferredLanguage, List<String> desiredTitles, List<String> locations, Set<WorkMode> workModes,
            List<SkillDto> skills, List<LanguageDto> languages, List<EntryDto> entries,
            Instant createdAt, Instant updatedAt, long version) {

        static ProfileResponse of(Profile profile) {
            return new ProfileResponse(profile.getId(), profile.getName(), profile.getObjective(),
                    profile.getSeniority(), profile.getMinMatchScore(), profile.getSalaryExpectation(),
                    profile.getSalaryCurrency(), profile.getAvailability(), profile.getRestrictions(),
                    profile.getPreferredLanguage(), profile.getDesiredTitles(), profile.getLocations(),
                    profile.getWorkModes(),
                    profile.getSkills().stream().map(s -> new SkillDto(s.getName(), s.getLevel(), s.getYears())).toList(),
                    profile.getLanguages().stream().map(l -> new LanguageDto(l.getCode(), l.getLevel())).toList(),
                    profile.getEntries().stream().map(e -> new EntryDto(e.getKind(), e.getTitle(), e.getOrganization(),
                            e.getStartDate(), e.getEndDate(), e.getDescription(), e.getUrl())).toList(),
                    profile.getCreatedAt(), profile.getUpdatedAt(), profile.getVersion());
        }
    }

    public record ProfileSummary(UUID id, String name, Seniority seniority, List<String> desiredTitles, int skillCount) {

        static ProfileSummary of(Profile profile) {
            return new ProfileSummary(profile.getId(), profile.getName(), profile.getSeniority(),
                    profile.getDesiredTitles(), profile.getSkills().size());
        }
    }
}
