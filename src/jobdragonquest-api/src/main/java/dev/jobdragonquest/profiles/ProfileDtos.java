package dev.jobdragonquest.profiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.jobdragonquest.common.domain.LanguageLevel;
import dev.jobdragonquest.common.domain.Seniority;
import dev.jobdragonquest.common.domain.WorkMode;
import dev.jobdragonquest.profiles.ProfileEnums.Degree;
import dev.jobdragonquest.profiles.ProfileEnums.EmploymentType;
import dev.jobdragonquest.profiles.ProfileEnums.EntryStatus;
import dev.jobdragonquest.profiles.ProfileEnums.SalaryPeriod;
import dev.jobdragonquest.profiles.ProfileEnums.TravelAvailability;
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
            @Pattern(regexp = "[a-z]{2,3}(-[A-Z]{2})?") String preferredLanguage,
            @Size(max = 10) List<@NotBlank @Size(max = 160) String> desiredTitles,
            @Size(max = 20) List<@NotBlank @Size(max = 160) String> locations,
            @Size(max = 3) Set<@NotNull WorkMode> workModes,
            @Size(max = 100) List<@Valid @NotNull SkillDto> skills,
            @Size(max = 20) List<@Valid @NotNull LanguageDto> languages,
            @Size(max = 150) List<@Valid @NotNull EntryDto> entries,
            /** Obrigatória na atualização: protege contra sobrescrever a edição de outra aba (lock otimista). */
            Long version,
            @Size(max = 220) String headline,
            @Size(max = 4000) String summary,
            @Size(max = 160) String currentTitle,
            @Size(max = 120) String city,
            @Size(max = 120) String stateRegion,
            @Pattern(regexp = "[A-Z]{2}") String country,
            Boolean requiresSponsorship,
            Boolean willingToRelocate,
            TravelAvailability travelAvailability,
            @Min(0) @Max(365) Integer noticePeriodDays,
            LocalDate availableFrom,
            @PositiveOrZero @DecimalMax("9999999999.99") BigDecimal currentSalary,
            SalaryPeriod salaryPeriod,
            @Size(max = 2048) String linkedinUrl,
            @Size(max = 2048) String githubUrl,
            @Size(max = 2048) String portfolioUrl,
            @Size(max = 2048) String websiteUrl,
            @Size(max = 8000) String coverLetter,
            Boolean disability,
            @Size(max = 500) String disabilityDetails,
            @Size(max = 30) List<@NotNull @Pattern(regexp = "[A-Z]{2}") String> workAuthorization,
            @Size(max = 10) Set<@NotNull EmploymentType> employmentTypes,
            @Size(max = 100) List<@NotBlank @Size(max = 160) String> excludedCompanies,
            @Size(max = 50) List<@NotBlank @Size(max = 80) String> includeKeywords,
            @Size(max = 50) List<@NotBlank @Size(max = 80) String> excludeKeywords,
            @Size(max = 20) List<@NotBlank @Size(max = 120) String> industries,
            @Size(max = 50) List<@Valid @NotNull AnswerDto> answers) {
    }

    /**
     * Competência. O tempo vem em anos, meses e dias. Por compatibilidade, {@code years} ainda aceita decimal
     * (1,5 vira 1 ano e 6 meses) quando meses e dias não vêm.
     */
    public record SkillDto(
            @NotBlank @Size(max = 80) String name,
            @Min(1) @Max(5) int level,
            @DecimalMin("0.0") @DecimalMax("60.0") BigDecimal years,
            @Min(0) @Max(11) Integer months,
            @Min(0) @Max(30) Integer days) {
    }

    /** Idioma pelo nome por extenso ({@code name}: "Inglês", "English", "Inglés") ou pelo código ISO ({@code code}). */
    public record LanguageDto(
            @Size(max = 8) String code,
            @Size(max = 60) String name,
            @NotNull LanguageLevel level) {
    }

    public record EntryDto(
            @NotNull ProfileEntry.Kind kind,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 200) String organization,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 4000) String description,
            @Size(max = 2048) String url,
            @Size(max = 160) String location,
            EmploymentType employmentType,
            Boolean current,
            Degree degree,
            @Size(max = 160) String fieldOfStudy,
            EntryStatus status,
            @Size(max = 120) String credentialId,
            LocalDate expirationDate,
            @Min(0) @Max(100000) Integer hours,
            @Size(max = 500) String skills) {
    }

    public record AnswerDto(@NotBlank @Size(max = 500) String question, @NotBlank @Size(max = 4000) String answer) {
    }

    public record SkillView(String name, int level, Integer years, Integer months, Integer days, BigDecimal totalYears) {
    }

    public record LanguageView(String code, LanguageLevel level) {
    }

    public record ProfileResponse(
            UUID id, String name, String objective, Seniority seniority, int minMatchScore,
            BigDecimal salaryExpectation, String salaryCurrency, String availability, String restrictions,
            String preferredLanguage, List<String> desiredTitles, List<String> locations, Set<WorkMode> workModes,
            List<SkillView> skills, List<LanguageView> languages, List<EntryDto> entries,
            String headline, String summary, String currentTitle, String city, String stateRegion, String country,
            Boolean requiresSponsorship, Boolean willingToRelocate, TravelAvailability travelAvailability,
            Integer noticePeriodDays, LocalDate availableFrom, BigDecimal currentSalary, SalaryPeriod salaryPeriod,
            String linkedinUrl, String githubUrl, String portfolioUrl, String websiteUrl, String coverLetter,
            Boolean disability, String disabilityDetails, List<String> workAuthorization, Set<EmploymentType> employmentTypes,
            List<String> excludedCompanies, List<String> includeKeywords, List<String> excludeKeywords,
            List<String> industries, List<AnswerDto> answers, Instant createdAt, Instant updatedAt, long version) {

        static ProfileResponse of(Profile p) {
            Profile.ProfileDetails d = p.getDetails();
            return new ProfileResponse(p.getId(), p.getName(), p.getObjective(), p.getSeniority(), p.getMinMatchScore(),
                    p.getSalaryExpectation(), p.getSalaryCurrency(), p.getAvailability(), p.getRestrictions(),
                    p.getPreferredLanguage(), p.getDesiredTitles(), p.getLocations(), p.getWorkModes(),
                    p.getSkills().stream().map(s -> new SkillView(s.getName(), s.getLevel(), s.getExpYears(),
                            s.getExpMonths(), s.getExpDays(), s.getYears())).toList(),
                    p.getLanguages().stream().map(l -> new LanguageView(l.getCode(), l.getLevel())).toList(),
                    p.getEntries().stream().map(ProfileResponse::entry).toList(),
                    d.headline(), d.summary(), d.currentTitle(), d.city(), d.stateRegion(), d.country(),
                    d.requiresSponsorship(), d.willingToRelocate(), d.travelAvailability(), d.noticePeriodDays(),
                    d.availableFrom(), d.currentSalary(), d.salaryPeriod(), d.linkedinUrl(), d.githubUrl(), d.portfolioUrl(),
                    d.websiteUrl(), d.coverLetter(), d.disability(), d.disabilityDetails(), d.workAuthorization(),
                    d.employmentTypes(), d.excludedCompanies(), d.includeKeywords(), d.excludeKeywords(), d.industries(),
                    d.answers().stream().map(a -> new AnswerDto(a.getQuestion(), a.getAnswer())).toList(),
                    p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
        }

        private static EntryDto entry(ProfileEntry e) {
            ProfileEntry.Details x = e.getDetails();
            return new EntryDto(e.getKind(), e.getTitle(), e.getOrganization(), e.getStartDate(), e.getEndDate(),
                    e.getDescription(), e.getUrl(), x.location(), x.employmentType(), x.current(), x.degree(),
                    x.fieldOfStudy(), x.status(), x.credentialId(), x.expirationDate(), x.hours(), x.skills());
        }
    }

    public record ProfileSummary(UUID id, String name, Seniority seniority, List<String> desiredTitles, int skillCount,
            String headline) {

        static ProfileSummary of(Profile profile) {
            return new ProfileSummary(profile.getId(), profile.getName(), profile.getSeniority(),
                    profile.getDesiredTitles(), profile.getSkills().size(), profile.getDetails().headline());
        }
    }
}
