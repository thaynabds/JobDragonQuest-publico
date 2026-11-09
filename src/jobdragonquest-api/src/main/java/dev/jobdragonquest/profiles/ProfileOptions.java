package dev.jobdragonquest.profiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import dev.jobdragonquest.common.domain.Seniority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Opções de busca, de candidatura e de currículo do perfil. Guardadas como JSON validado, só da própria conta. Os dados
 * sensíveis (nascimento, gênero, diversidade, serviço militar) nunca entram na compatibilidade e só aparecem no currículo
 * quando a seção correspondente é ligada em {@code resumeVisible}.
 */
public record ProfileOptions(
        // Busca
        @Size(max = 13) Set<@NotNull Seniority> targetSeniorities,
        @Size(max = 10) List<@NotBlank @Size(max = 60) String> timeZones,
        @Size(max = 10) List<@NotBlank @Size(max = 60) String> jobLanguages,
        @Min(0) @Max(1000) Integer locationRadiusKm,
        // Candidatura
        CoverLetterMode coverLetterMode,
        @Size(max = 1000) String writingStyle,
        @Pattern(regexp = "\\+?[0-9-]{1,8}") String phoneCountryCode,
        @Size(max = 30) String phone,
        Boolean phoneWhatsapp,
        @Size(max = 30) String secondaryPhone,
        Boolean secondaryPhoneWhatsapp,
        @Email @Size(max = 254) String contactEmail,
        Boolean contactPermission,
        @Size(max = 20) String postalCode,
        @Size(max = 3) List<@NotBlank @Size(max = 60) String> nationalities,
        @PositiveOrZero @DecimalMax("9999999999.99") BigDecimal expectedYearlySalary,
        @PositiveOrZero @DecimalMax("9999999999.99") BigDecimal currentHourlySalary,
        @PositiveOrZero @DecimalMax("9999999999.99") BigDecimal expectedHourlySalary,
        @PositiveOrZero @DecimalMax("9999999999.99") BigDecimal salaryMin,
        @PositiveOrZero @DecimalMax("9999999999.99") BigDecimal salaryMax,
        @PositiveOrZero @DecimalMax("9999999999.99") BigDecimal salaryConsiderFrom,
        Boolean transportAid,
        Boolean practicalExperience,
        @Size(max = 40) String workload,
        // Dados sensíveis e opcionais
        @Size(max = 120) String socialName,
        @Size(max = 60) String genderIdentity,
        LocalDate birthDate,
        Boolean over18,
        @Size(max = 10) String gpa,
        Answer military,
        @Size(max = 6) Set<@NotNull Diversity> diversity,
        @Size(max = 30) List<@NotBlank @Size(max = 120) String> diversityOther,
        @Size(max = 120) String drivingLicense,
        @Size(max = 120) String securityClearance,
        // Currículo
        @Size(max = 50) List<@NotBlank @Size(max = 80) String> softSkills,
        @Size(max = 7) List<@Valid @NotNull ContactDay> contactHours,
        @Size(max = 20) Set<@NotNull ResumeSection> resumeVisible,
        @Size(max = 20) Set<@NotNull ResumeField> resumeHidden,
        // Links extras e documentos no idioma da vaga
        @Size(max = 500) List<@Valid @NotNull ExtraLink> extraLinks,
        Boolean documentsInJobLanguage,
        Boolean autoTranslate,
        @Size(max = 3) List<@Valid @NotNull Translation> translations) {

    public enum CoverLetterMode { GENERATED, OWN }

    public enum Answer { YES, NO, PREFER_NOT_TO_SAY }

    public enum Diversity { LGBTQIA, WOMAN, DISABILITY, BLACK, BROWN, INDIGENOUS }

    /** Dados que só entram no currículo se a pessoa ligar. */
    public enum ResumeSection {
        CONTACT, SOFT_SKILLS, DIVERSITY, CONTACT_HOURS, WORK_PREFERENCES, SALARY, SOCIAL_NAME, GENDER, BIRTH_DATE, GPA,
        NATIONALITY, POSTAL_CODE, MILITARY
    }

    /** Dados que entram no currículo por padrão e que a pessoa pode tirar. */
    public enum ResumeField {
        HEADLINE, CURRENT_TITLE, LOCATION, SUMMARY, LINKEDIN, GITHUB, PORTFOLIO, WEBSITE, PHONE, SECONDARY_PHONE, EMAIL
    }

    public enum Weekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

    /** Link profissional com nome livre (ex.: Behance, Lattes). */
    public record ExtraLink(@NotBlank @Size(max = 60) String label, @NotBlank @Size(max = 2048) String url, Boolean showInResume) {
    }

    /** Textos que a própria pessoa escreveu em outro idioma, usados quando a vaga está nesse idioma. */
    public record Translation(
            @NotNull @Pattern(regexp = "pt|en|es") String language,
            @Size(max = 220) String headline,
            @Size(max = 4000) String summary,
            @Size(max = 8000) String coverLetter) {
    }

    /** Horários de contato de um dia, no formato HH:mm. */
    public record ContactDay(@NotNull Weekday day, @Size(max = 4) List<@Valid @NotNull Slot> slots) {
    }

    public record Slot(
            @NotNull @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String start,
            @NotNull @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String end) {
    }
}
