package dev.jobfly.profiles;

import java.time.Clock;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jobfly.audit.AuditService;
import dev.jobfly.common.domain.WorkMode;
import dev.jobfly.common.error.DomainException;
import dev.jobfly.common.error.NotFoundException;
import dev.jobfly.common.text.SkillNormalizer;
import dev.jobfly.common.text.UrlCanonicalizer;
import dev.jobfly.profiles.Profile.ProfileData;
import dev.jobfly.profiles.ProfileDtos.EntryDto;
import dev.jobfly.profiles.ProfileDtos.LanguageDto;
import dev.jobfly.profiles.ProfileDtos.ProfileRequest;
import dev.jobfly.profiles.ProfileDtos.ProfileResponse;
import dev.jobfly.profiles.ProfileDtos.ProfileSummary;
import dev.jobfly.profiles.ProfileDtos.SkillDto;

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
                strip(request.desiredTitles()), strip(request.locations()), workModes, skills, languages, entries);
    }

    private static ProfileSkill toSkill(SkillDto dto) {
        String name = dto.name().strip();
        return new ProfileSkill(name, SkillNormalizer.normalize(name), dto.level(), dto.years());
    }

    private static ProfileLanguage toLanguage(LanguageDto dto) {
        return new ProfileLanguage(dto.code(), dto.level());
    }

    private static ProfileEntry toEntry(EntryDto dto) {
        if (dto.startDate() != null && dto.endDate() != null && dto.endDate().isBefore(dto.startDate())) {
            throw DomainException.badRequest("invalid_period", "A data final não pode ser anterior à inicial: " + dto.title());
        }
        return new ProfileEntry(dto.kind(), dto.title().strip(), blankToNull(dto.organization()), dto.startDate(),
                dto.endDate(), blankToNull(dto.description()), UrlCanonicalizer.optional(dto.url()));
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
