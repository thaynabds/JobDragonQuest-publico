package dev.jobdragonquest.contests;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.error.NotFoundException;
import dev.jobdragonquest.common.text.UrlCanonicalizer;
import dev.jobdragonquest.contests.ContestDtos.ContestRequest;
import dev.jobdragonquest.contests.ContestDtos.ContestResponse;
import dev.jobdragonquest.contests.ContestDtos.DocumentView;
import dev.jobdragonquest.contests.ContestDtos.MatchView;
import dev.jobdragonquest.contests.ContestDtos.Stage;
import dev.jobdragonquest.contests.ContestDtos.WatchRequest;
import dev.jobdragonquest.contests.ContestDtos.WatchView;
import dev.jobdragonquest.jobs.link.SafeWebFetcher;

/** Cadastro de concursos, etapas e páginas vigiadas. Recurso de outra conta responde 404. */
@Service
public class ContestService {

    static final int MAX_CONTESTS = 100;
    static final int MAX_WATCHES = 20;

    private final ContestRepository contests;
    private final ContestWatchRepository watches;
    private final ContestDocumentRepository documents;
    private final ContestMatchRepository matches;
    private final Clock clock;

    ContestService(ContestRepository contests, ContestWatchRepository watches,
            ContestDocumentRepository documents, ContestMatchRepository matches, Clock clock) {
        this.contests = contests;
        this.watches = watches;
        this.documents = documents;
        this.matches = matches;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ContestResponse> list(UUID userId) {
        return contests.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ContestResponse get(UUID userId, UUID id) {
        return view(load(userId, id));
    }

    @Transactional
    public ContestResponse create(UUID userId, ContestRequest request) {
        if (contests.countByUserId(userId) >= MAX_CONTESTS) {
            throw DomainException.badRequest("contest_limit", "Limite de concursos atingido");
        }
        Contest contest = new Contest();
        contest.id = UUID.randomUUID();
        contest.userId = userId;
        contest.createdAt = clock.instant();
        apply(contest, request);
        return view(contests.save(contest));
    }

    @Transactional
    public ContestResponse update(UUID userId, UUID id, ContestRequest request) {
        Contest contest = load(userId, id);
        apply(contest, request);
        return view(contest);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        contests.delete(load(userId, id));
    }

    @Transactional
    public ContestResponse addWatch(UUID userId, UUID id, WatchRequest request) {
        Contest contest = load(userId, id);
        if (watches.countByContestId(id) >= MAX_WATCHES) {
            throw DomainException.badRequest("watch_limit", "Limite de páginas vigiadas atingido");
        }
        String url = UrlCanonicalizer.optional(request.url().strip());
        SafeWebFetcher.checkDestination(URI.create(url));
        if (watches.findByContestIdOrderByCreatedAt(id).stream().anyMatch(watch -> watch.url.equals(url))) {
            throw DomainException.conflict("watch_exists", "Esta página já está vigiada");
        }
        ContestWatch watch = new ContestWatch();
        watch.id = UUID.randomUUID();
        watch.contestId = id;
        watch.url = url;
        watch.label = blank(request.label());
        watch.status = "NEW";
        watch.createdAt = clock.instant();
        watches.save(watch);
        return view(contest);
    }

    @Transactional
    public void deleteWatch(UUID userId, UUID id, UUID watchId) {
        load(userId, id);
        watches.delete(watches.findByIdAndContestId(watchId, id).orElseThrow(() -> new NotFoundException("Página vigiada")));
    }

    Contest load(UUID userId, UUID id) {
        return contests.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Concurso"));
    }

    private void apply(Contest c, ContestRequest r) {
        c.name = r.name().strip();
        c.organ = blank(r.organ());
        c.board = blank(r.board());
        c.notice = blank(r.notice());
        c.roleName = blank(r.roleName());
        c.stateCode = r.stateCode();
        c.city = blank(r.city());
        c.registration = blank(r.registration());
        c.searchName = blank(r.searchName());
        c.validUntil = r.validUntil();
        c.notes = blank(r.notes());
        List<Stage> stages = r.stages() == null ? List.of() : r.stages().stream()
                .map(s -> new Stage(s.title().strip(), s.date(), s.deadline(), s.status(), UrlCanonicalizer.optional(blank(s.url())), blank(s.note())))
                .toList();
        c.stages = ContestJson.write(stages);
        Instant now = clock.instant();
        c.updatedAt = now;
    }

    private ContestResponse view(Contest c) {
        return new ContestResponse(c.id, c.name, c.organ, c.board, c.notice, c.roleName, c.stateCode, c.city, c.registration,
                c.searchName, c.validUntil, c.notes, ContestJson.read(c.stages),
                watches.findByContestIdOrderByCreatedAt(c.id).stream()
                        .map(w -> new WatchView(w.id, w.url, w.label, w.status, w.lastCheckedAt)).toList(),
                documents.findTop50ByContestIdOrderByReadAtDesc(c.id).stream()
                        .map(d -> new DocumentView(d.url, d.status, d.readAt)).toList(),
                matches.findTop100ByContestIdOrderByFoundAtDesc(c.id).stream()
                        .map(m -> new MatchView(m.url, m.kind, m.confidence, m.line, m.foundAt)).toList());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
