package dev.jobdragonquest.profiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jobdragonquest.audit.AuditService;
import dev.jobdragonquest.common.domain.WorkMode;
import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.error.NotFoundException;
import dev.jobdragonquest.common.text.SkillNormalizer;
import dev.jobdragonquest.common.text.UrlCanonicalizer;
import dev.jobdragonquest.profiles.Profile.ProfileData;
import dev.jobdragonquest.profiles.Profile.ProfileDetails;
import dev.jobdragonquest.profiles.ProfileEnums.EmploymentType;
import dev.jobdragonquest.profiles.ProfileDtos.EntryDto;
import dev.jobdragonquest.profiles.ProfileDtos.LanguageDto;
import dev.jobdragonquest.profiles.ProfileDtos.ProfileRequest;
import dev.jobdragonquest.profiles.ProfileDtos.ProfileResponse;
import dev.jobdragonquest.profiles.ProfileDtos.ProfileSummary;
import dev.jobdragonquest.profiles.ProfileDtos.SkillDto;

/**
 * Fachada do módulo de perfis. Toda operação recebe o id do usuário dono, e nenhuma consulta lê perfil de outro usuário.
 */
@Service
public class ProfileService {

    static final int MAX_PROFILES_PER_USER = 30;
    private static final int DEFAULT_MIN_MATCH = 60;

    private final ProfileRepository repository;
    private final AuditService audit;
    private final Clock clock;

    ProfileService(ProfileRepository repository, AuditService audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProfileSummary> list(UUID userId) {
        return repository.findByUserIdOrderByNameAsc(userId).stream().map(ProfileSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID userId, UUID profileId) {
        return ProfileResponse.of(load(userId, profileId));
    }

    @Transactional(readOnly = true)
    public List<ProfileSnapshot> snapshots(UUID userId) {
        return repository.findByUserIdOrderByNameAsc(userId).stream().map(ProfileSnapshot::of).toList();
    }

    @Transactional(readOnly = true)
    public ProfileSnapshot snapshot(UUID userId, UUID profileId) {
        return ProfileSnapshot.of(load(userId, profileId));
    }

    /** Confere que o perfil existe e é do usuário. Usado por outros módulos antes de associar o perfil a algo. */
    @Transactional(readOnly = true)
    public void requireOwned(UUID userId, UUID profileId) {
        load(userId, profileId);
    }

    private Profile load(UUID userId, UUID profileId) {
        return repository.findByIdAndUserId(profileId, userId).orElseThrow(() -> new NotFoundException("Perfil"));
    }

    @Transactional
    public ProfileResponse create(UUID userId, ProfileRequest request) {
        if (repository.countByUserId(userId) >= MAX_PROFILES_PER_USER) {
            throw DomainException.badRequest("profile_limit", "Limite de " + MAX_PROFILES_PER_USER + " perfis atingido");
        }
        String name = request.name().strip();
        if (repository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw DomainException.conflict("profile_name_taken", "Já existe um perfil com este nome");
        }
        Profile profile = new Profile(userId, clock.instant());
        profile.update(toData(request), clock.instant());
        repository.saveAndFlush(profile);
        audit.record(userId, "profile.created", "profile", profile.getId(), null);
        return ProfileResponse.of(profile);
    }

    static final int MAX_ENTRIES = 150;

    /**
     * Acrescenta um item ao perfil (issue #124): é o passo de confirmação depois de ler um certificado ou documento.
     * Não mexe no resto do perfil.
     */
    @Transactional
    public ProfileResponse appendEntry(UUID userId, UUID profileId, EntryDto dto) {
        Profile profile = load(userId, profileId);
        if (profile.entryCount() >= MAX_ENTRIES) {
            throw DomainException.badRequest("profile_limit", "O perfil já tem " + MAX_ENTRIES + " itens.");
        }
        profile.addEntry(toEntry(dto), clock.instant());
        repository.flush();
        audit.record(userId, "profile.entry_added", "profile", profileId, dto.kind().name());
        return ProfileResponse.of(profile);
    }

    @Transactional
    public ProfileResponse update(UUID userId, UUID profileId, ProfileRequest request) {
        Profile profile = load(userId, profileId);
        if (request.version() == null || request.version() != profile.getVersion()) {
            throw DomainException.conflict("stale_version", "O perfil mudou desde que foi aberto. Recarregue antes de salvar.");
        }
        String name = request.name().strip();
        if (repository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, profileId)) {
            throw DomainException.conflict("profile_name_taken", "Já existe um perfil com este nome");
        }
        profile.update(toData(request), clock.instant());
        repository.flush();
        audit.record(userId, "profile.updated", "profile", profileId, "versão " + profile.getVersion());
        return ProfileResponse.of(profile);
    }

    @Transactional
    public void delete(UUID userId, UUID profileId) {
        Profile profile = load(userId, profileId);
        repository.delete(profile);
        audit.record(userId, "profile.deleted", "profile", profileId, null);
    }

    private static ProfileData toData(ProfileRequest request) {
        List<ProfileSkill> skills = nullToEmpty(request.skills()).stream().map(ProfileService::toSkill).toList();
        rejectDuplicates(skills.stream().map(ProfileSkill::getNormalized).toList(), "duplicate_skill", "Competência repetida");
        List<ProfileLanguage> languages = nullToEmpty(request.languages()).stream().map(ProfileService::toLanguage).toList();
        rejectDuplicates(languages.stream().map(ProfileLanguage::getCode).toList(), "duplicate_language", "Idioma repetido");
        List<ProfileEntry> entries = nullToEmpty(request.entries()).stream().map(ProfileService::toEntry).toList();
        Set<WorkMode> workModes = request.workModes() == null || request.workModes().isEmpty()
                ? EnumSet.noneOf(WorkMode.class) : EnumSet.copyOf(request.workModes());
        return new ProfileData(request.name().strip(), blankToNull(request.objective()), request.seniority(),
                request.minMatchScore() == null ? DEFAULT_MIN_MATCH : request.minMatchScore(),
                request.salaryExpectation(), request.salaryExpectation() == null ? null : request.salaryCurrency(),
                blankToNull(request.availability()), blankToNull(request.restrictions()),
                request.preferredLanguage() == null ? "pt-BR" : request.preferredLanguage(),
                strip(request.desiredTitles()), strip(request.locations()), workModes, skills, languages, entries,
                toDetails(request), request.automationMode());
    }

    private static ProfileDetails toDetails(ProfileRequest r) {
        Set<EmploymentType> types = r.employmentTypes() == null || r.employmentTypes().isEmpty()
                ? EnumSet.noneOf(EmploymentType.class) : EnumSet.copyOf(r.employmentTypes());
        List<ProfileAnswer> answers = nullToEmpty(r.answers()).stream()
                .map(a -> new ProfileAnswer(a.question().strip(), a.answer().strip())).toList();
        boolean disability = Boolean.TRUE.equals(r.disability());
        if (r.options() != null && r.options().extraLinks() != null) {
            r.options().extraLinks().forEach(extra -> link(extra.url()));
        }
        return new ProfileDetails(blankToNull(r.headline()), blankToNull(r.summary()), blankToNull(r.currentTitle()),
                blankToNull(r.city()), blankToNull(r.stateRegion()), r.country(), r.requiresSponsorship(),
                r.willingToRelocate(), r.travelAvailability(), r.noticePeriodDays(), r.availableFrom(), r.currentSalary(),
                r.salaryPeriod(), link(r.linkedinUrl()), link(r.githubUrl()), link(r.portfolioUrl()), link(r.websiteUrl()),
                blankToNull(r.coverLetter()), r.disability(), disability ? blankToNull(r.disabilityDetails()) : null,
                strip(r.workAuthorization()), types, strip(r.excludedCompanies()), strip(r.includeKeywords()),
                strip(r.excludeKeywords()), strip(r.industries()), answers, r.options());
    }

    /** Link do perfil: só http ou https (nada de javascript:, data: ou file:). */
    private static String link(String value) {
        return UrlCanonicalizer.optional(blankToNull(value));
    }

    /**
     * Anos, meses e dias. Sem meses e dias, um {@code years} decimal (formato antigo) vira anos e meses: 1,5 = 1 ano e
     * 6 meses.
     */
    static ProfileSkill toSkill(SkillDto dto) {
        String name = dto.name().strip();
        Integer years = null;
        Integer months = dto.months();
        Integer days = dto.days();
        if (dto.years() != null) {
            BigDecimal whole = dto.years().setScale(0, RoundingMode.DOWN);
            years = whole.intValueExact();
            BigDecimal fraction = dto.years().subtract(whole);
            if (fraction.signum() > 0) {
                if (months != null || days != null) {
                    throw DomainException.badRequest("invalid_skill_time",
                            "Use anos inteiros quando informar meses ou dias: " + name);
                }
                int extra = fraction.multiply(BigDecimal.valueOf(12)).setScale(0, RoundingMode.HALF_UP).intValue();
                if (extra == 12) {
                    years++;
                    extra = 0;
                }
                months = extra;
            }
        }
        return new ProfileSkill(name, SkillNormalizer.normalize(name), dto.level(), years, months, days);
    }

    private static ProfileLanguage toLanguage(LanguageDto dto) {
        String typed = dto.name() != null && !dto.name().isBlank() ? dto.name() : dto.code();
        if (typed == null || typed.isBlank()) {
            throw DomainException.badRequest("unknown_language", "Informe o nome do idioma");
        }
        String code = LanguageCatalog.resolve(typed).orElseThrow(() -> DomainException.badRequest("unknown_language",
                "Idioma não reconhecido: " + typed.strip()));
        return new ProfileLanguage(code, dto.level());
    }

    private static ProfileEntry toEntry(EntryDto dto) {
        if (dto.startDate() != null && dto.endDate() != null && dto.endDate().isBefore(dto.startDate())) {
            throw DomainException.badRequest("invalid_period", "A data final não pode ser anterior à inicial: " + dto.title());
        }
        if (dto.startDate() != null && dto.expirationDate() != null && dto.expirationDate().isBefore(dto.startDate())) {
            throw DomainException.badRequest("invalid_period", "A validade não pode ser anterior à emissão: " + dto.title());
        }
        boolean current = Boolean.TRUE.equals(dto.current());
        ProfileEntry.Details details = new ProfileEntry.Details(blankToNull(dto.location()), dto.employmentType(),
                current ? Boolean.TRUE : dto.current(), dto.degree(), blankToNull(dto.fieldOfStudy()), dto.status(),
                blankToNull(dto.credentialId()), dto.expirationDate(), dto.hours(), blankToNull(dto.skills()));
        return new ProfileEntry(dto.kind(), dto.title().strip(), blankToNull(dto.organization()), dto.startDate(),
                current ? null : dto.endDate(), blankToNull(dto.description()), UrlCanonicalizer.optional(dto.url()), details);
    }

    private static void rejectDuplicates(List<String> values, String code, String message) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (!seen.add(value)) {
                throw DomainException.badRequest(code, message + ": " + value);
            }
        }
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static List<String> strip(List<String> values) {
        return nullToEmpty(values).stream().map(String::strip).distinct().toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
