package dev.jobfly.sources;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jobfly.audit.AuditService;
import dev.jobfly.common.error.DomainException;
import dev.jobfly.common.error.NotFoundException;
import dev.jobfly.common.text.UrlCanonicalizer;
import dev.jobfly.profiles.ProfileService;
import dev.jobfly.sources.SourceDtos.BulkCreateRequest;
import dev.jobfly.sources.SourceDtos.BulkCreateResult;
import dev.jobfly.sources.SourceDtos.BulkUpdateRequest;
import dev.jobfly.sources.SourceDtos.CatalogEntry;
import dev.jobfly.sources.SourceDtos.DuplicateSource;
import dev.jobfly.sources.SourceDtos.RunResponse;
import dev.jobfly.sources.SourceDtos.SourceInput;
import dev.jobfly.sources.SourceDtos.SourceResponse;
import dev.jobfly.sources.SourceDtos.UpdateSourceRequest;
import dev.jobfly.sources.SourceKind.CollectionMode;

/**
 * Fontes vigiadas: cadastro e edição, um a um ou em lote, sempre do próprio usuário e ligadas só a perfis dele.
 */
@Service
public class SourceService {

    static final int MAX_SOURCES_PER_USER = 200;

    private final WatchedSourceRepository sources;
    private final SourceRunRepository runs;
    private final ProfileService profiles;
    private final AuditService audit;
    private final Clock clock;

    SourceService(WatchedSourceRepository sources, SourceRunRepository runs, ProfileService profiles, AuditService audit,
            Clock clock) {
        this.sources = sources;
        this.runs = runs;
        this.profiles = profiles;
        this.audit = audit;
        this.clock = clock;
    }

    public List<CatalogEntry> catalog() {
        return Arrays.stream(SourceKind.values())
                .map(kind -> new CatalogEntry(kind, kind.label(), kind.mode(), kind.needsIdentifier()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SourceResponse> list(UUID userId, UUID profileId) {
        return sources.findByUserIdOrderByNameAsc(userId).stream()
                .filter(source -> profileId == null || source.getProfileIds().contains(profileId))
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public SourceResponse get(UUID userId, UUID sourceId) {
        return response(load(userId, sourceId));
    }

    /**
     * Cadastra várias fontes de uma vez para os mesmos perfis. Tudo ou nada: um item inválido recusa o lote inteiro.
     * Fontes que já existem (mesmo board ou mesma URL) não são recriadas e voltam em {@code duplicates}.
     */
    @Transactional
    public BulkCreateResult createBulk(UUID userId, BulkCreateRequest request) {
        Set<UUID> profileIds = ownedProfiles(userId, request.profileIds());
        int frequency = request.frequencyMinutes() == null ? SourceDtos.DEFAULT_FREQUENCY : request.frequencyMinutes();
        List<String> keywords = keywords(request.keywords());
        Instant now = clock.instant();
        if (sources.countByUserId(userId) + request.sources().size() > MAX_SOURCES_PER_USER) {
            throw DomainException.badRequest("source_limit", "Limite de " + MAX_SOURCES_PER_USER + " fontes por conta");
        }

        List<SourceResponse> created = new ArrayList<>();
        List<DuplicateSource> duplicates = new ArrayList<>();
        Map<String, Integer> seenInRequest = new HashMap<>();
        for (int index = 0; index < request.sources().size(); index++) {
            SourceInput input = request.sources().get(index);
            Target target = target(input.kind(), input.identifier(), input.url());
            Optional<WatchedSource> existing = sources.findByUserIdAndDedupKey(userId, target.dedupKey());
            if (existing.isPresent()) {
                duplicates.add(new DuplicateSource(index, existing.get().getId(), existing.get().getName()));
                continue;
            }
            Integer firstIndex = seenInRequest.putIfAbsent(target.dedupKey(), index);
            if (firstIndex != null) {
                duplicates.add(new DuplicateSource(index, null, input.name().strip()));
                continue;
            }
            WatchedSource source = new WatchedSource(userId, input.kind(), target.dedupKey(), now);
            source.configure(input.name().strip(), target.identifier(), target.url(), frequency, keywords, now);
            source.replaceProfiles(profileIds, now);
            sources.save(source);
            created.add(response(source));
        }
        sources.flush();
        audit.record(userId, "source.bulk_created", "source", null,
                created.size() + " criada(s), " + duplicates.size() + " duplicada(s)");
        return new BulkCreateResult(created, duplicates);
    }

    @Transactional
    public SourceResponse update(UUID userId, UUID sourceId, UpdateSourceRequest request) {
        WatchedSource source = load(userId, sourceId);
        if (request.version() != source.getVersion()) {
            throw DomainException.conflict("stale_version", "A fonte mudou desde que foi aberta. Recarregue antes de salvar.");
        }
        Target target = target(source.getKind(), request.identifier(), request.url());
        sources.findByUserIdAndDedupKey(userId, target.dedupKey())
                .filter(other -> !other.getId().equals(sourceId))
                .ifPresent(other -> {
                    throw DomainException.conflict("source_duplicate", "Outra fonte já vigia este endereço: " + other.getName());
                });
        Instant now = clock.instant();
        Set<UUID> profileIds = ownedProfiles(userId, request.profileIds());
        source.configure(request.name().strip(), target.identifier(), target.url(), request.frequencyMinutes(),
                keywords(request.keywords()), now);
        source.updateDedupKey(target.dedupKey());
        source.replaceProfiles(profileIds, now);
        source.changeFrequency(request.frequencyMinutes(), now);
        source.enable(request.enabled(), now);
        sources.flush();
        audit.record(userId, "source.updated", "source", sourceId, null);
        return response(source);
    }

    /** Aplica a mesma mudança a várias fontes. Id desconhecido ou de outro usuário recusa o lote inteiro (404). */
    @Transactional
    public List<SourceResponse> updateBulk(UUID userId, BulkUpdateRequest request) {
        boolean replace = request.setProfileIds() != null;
        boolean incremental = request.addProfileIds() != null || request.removeProfileIds() != null;
        if (replace && incremental) {
            throw DomainException.badRequest("conflicting_profile_change",
                    "Use setProfileIds sozinho, ou addProfileIds/removeProfileIds");
        }
        if (request.enabled() == null && request.frequencyMinutes() == null && !replace && !incremental) {
            throw DomainException.badRequest("empty_bulk_update", "Nada para alterar");
        }
        List<WatchedSource> selected = sources.findByUserIdAndIdIn(userId, request.sourceIds());
        if (selected.size() != request.sourceIds().size()) {
            throw new NotFoundException("Fonte");
        }
        Set<UUID> toSet = replace ? ownedProfiles(userId, request.setProfileIds()) : Set.of();
        Set<UUID> toAdd = request.addProfileIds() == null ? Set.of() : ownedProfiles(userId, request.addProfileIds());
        Set<UUID> toRemove = request.removeProfileIds() == null ? Set.of() : new HashSet<>(request.removeProfileIds());
        Instant now = clock.instant();
        for (WatchedSource source : selected) {
            if (replace) {
                source.replaceProfiles(toSet, now);
            }
            if (!toAdd.isEmpty()) {
                source.addProfiles(toAdd, now);
            }
            if (!toRemove.isEmpty()) {
                source.removeProfiles(toRemove, now);
            }
            if (request.frequencyMinutes() != null) {
                source.changeFrequency(request.frequencyMinutes(), now);
            }
            if (request.enabled() != null) {
                source.enable(request.enabled(), now);
            }
        }
        sources.flush();
        audit.record(userId, "source.bulk_updated", "source", null, selected.size() + " fonte(s)");
        return selected.stream().map(this::response).sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
    }

    /** Últimas 20 execuções (coletas ou conferências) da fonte, da mais recente para a mais antiga. */
    @Transactional(readOnly = true)
    public List<RunResponse> runs(UUID userId, UUID sourceId) {
        load(userId, sourceId);
        return runs.findBySourceIdOrderByStartedAtDesc(sourceId, PageRequest.of(0, 20)).stream().map(RunResponse::of).toList();
    }

    @Transactional
    public void delete(UUID userId, UUID sourceId) {
        WatchedSource source = load(userId, sourceId);
        sources.delete(source);
        audit.record(userId, "source.deleted", "source", sourceId, null);
    }

    WatchedSource load(UUID userId, UUID sourceId) {
        return sources.findByIdAndUserId(sourceId, userId).orElseThrow(() -> new NotFoundException("Fonte"));
    }

    SourceResponse response(WatchedSource s) {
        return new SourceResponse(s.getId(), s.getName(), s.getKind(), s.getKind().label(), s.getCollectionMode(),
                s.getIdentifier(), s.getUrl(), openUrl(s), s.getFrequencyMinutes(), s.isEnabled(), s.getProfileIds(),
                s.getKeywords(), s.getNextRunAt(), s.getLastRunAt(), s.getLastStatus(), s.getLastMessage(),
                s.getLastFound(), s.getConsecutiveFailures(), s.isDue(clock.instant()), s.getCreatedAt(), s.getVersion());
    }

    /** Página pública que a pessoa pode abrir para conferir a fonte. */
    static String openUrl(WatchedSource source) {
        String id = source.getIdentifier();
        return switch (source.getKind()) {
            case GREENHOUSE -> "https://boards.greenhouse.io/" + id;
            case LEVER -> "https://jobs.lever.co/" + id;
            case SMARTRECRUITERS -> "https://careers.smartrecruiters.com/" + id;
            case ASHBY -> "https://jobs.ashbyhq.com/" + id;
            default -> source.getUrl();
        };
    }

    private Set<UUID> ownedProfiles(UUID userId, Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        ids.forEach(id -> profiles.requireOwned(userId, id));
        return Set.copyOf(ids);
    }

    private static List<String> keywords(List<String> values) {
        return values == null ? List.of() : values.stream().map(String::strip).filter(v -> !v.isEmpty()).distinct().toList();
    }

    /** Valida o alvo da fonte e calcula a chave de deduplicação (tipo + board, ou tipo + URL canônica). */
    static Target target(SourceKind kind, String identifier, String url) {
        if (kind.mode() == CollectionMode.AUTOMATIC) {
            String id = identifier == null ? "" : identifier.strip();
            if (!SourceKind.IDENTIFIER.matcher(id).matches()) {
                throw DomainException.badRequest("invalid_identifier",
                        "Informe o identificador do board no " + kind.label() + " (letras, números, ponto, hífen ou sublinhado)");
            }
            return new Target(id, null, dedupKey(kind, id.toLowerCase(Locale.ROOT)));
        }
        if (url == null || url.isBlank()) {
            throw DomainException.badRequest("url_required", "Informe o endereço a vigiar no " + kind.label());
        }
        String canonical = UrlCanonicalizer.canonicalize(url);
        return new Target(null, canonical, dedupKey(kind, canonical));
    }

    private static String dedupKey(SourceKind kind, String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((kind.name() + "|" + value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }

    record Target(String identifier, String url, String dedupKey) {
    }
}
