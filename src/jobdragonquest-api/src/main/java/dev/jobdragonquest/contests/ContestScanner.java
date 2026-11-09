package dev.jobdragonquest.contests;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.contests.ContestDtos.ScanResult;
import dev.jobdragonquest.contests.ContestDtos.Stage;
import dev.jobdragonquest.documents.DocumentText;
import dev.jobdragonquest.jobs.link.SafeWebFetcher;
import dev.jobdragonquest.notifications.NotificationDraft;
import dev.jobdragonquest.notifications.NotificationPriority;
import dev.jobdragonquest.notifications.NotificationService;
import dev.jobdragonquest.notifications.NotificationType;

/**
 * Lê as páginas vigiadas e os PDFs novos que elas apontam. Um documento que não pôde ser lido gera aviso próprio, para
 * que a falta de aviso nunca seja entendida como "não foi chamada". Texto de documento é só dado.
 */
@Service
public class ContestScanner {

    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_NEW_DOCUMENTS = 5;

    private static final Logger log = LoggerFactory.getLogger(ContestScanner.class);
    private static final Pattern HREF = Pattern.compile("(?i)href\\s*=\\s*[\"']([^\"'#]+\\.pdf(?:\\?[^\"'#]*)?)[\"']");
    private static final Pattern HIDDEN = Pattern.compile("(?is)<(script|style)\\b.*?</\\1>");
    private static final Pattern BLOCK = Pattern.compile("(?i)</?(p|div|tr|li|br|h[1-6])\\b[^>]*>");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final int[] DEADLINE_DAYS = {7, 3, 1};

    private final ContestService service;
    private final ContestRepository contests;
    private final ContestWatchRepository watches;
    private final ContestDocumentRepository documents;
    private final ContestMatchRepository matches;
    private final SafeWebFetcher fetcher;
    private final NotificationService notifications;
    private final Clock clock;

    ContestScanner(ContestService service, ContestRepository contests, ContestWatchRepository watches,
            ContestDocumentRepository documents, ContestMatchRepository matches, SafeWebFetcher fetcher,
            NotificationService notifications, Clock clock) {
        this.service = service;
        this.contests = contests;
        this.watches = watches;
        this.documents = documents;
        this.matches = matches;
        this.fetcher = fetcher;
        this.notifications = notifications;
        this.clock = clock;
    }

    public ScanResult scan(UUID userId, UUID contestId) {
        return scan(service.load(userId, contestId));
    }

    /** Rodada do agendador: um concurso por vez, e a falha de um não interrompe os outros. */
    void scanAll() {
        for (Contest contest : contests.findAll()) {
            try {
                remindDeadlines(contest);
                scan(contest);
            } catch (RuntimeException ex) {
                log.warn("Leitura do concurso {} falhou: {}", contest.id, ex.getClass().getSimpleName());
            }
        }
    }

    private ScanResult scan(Contest contest) {
        int[] count = new int[5];
        for (ContestWatch watch : watches.findByContestIdOrderByCreatedAt(contest.id)) {
            count[0]++;
            scanWatch(contest, watch, count);
        }
        return new ScanResult(count[0], count[1], count[2], count[3], count[4]);
    }

    private void scanWatch(Contest contest, ContestWatch watch, int[] count) {
        URI uri = URI.create(watch.url);
        watch.lastCheckedAt = clock.instant();
        if (!allowedByRobots(uri)) {
            watch.status = "BLOCKED";
            watches.save(watch);
            unread(contest, watch.url, "A página não permite leitura automática. Acompanhe por conta própria.", count);
            return;
        }
        byte[] page;
        try {
            page = fetcher.fetchBytes(uri, MAX_BYTES);
        } catch (DomainException ex) {
            watch.status = "FAILED";
            watches.save(watch);
            unread(contest, watch.url, "A página não pôde ser lida nesta rodada.", count);
            return;
        }
        String hash = sha256(page);
        boolean changed = !hash.equals(watch.lastHash);
        if (changed && watch.lastHash != null) {
            count[1]++;
            notify(contest, NotificationType.SOURCE_TO_CHECK, NotificationPriority.MEDIUM, "changed:" + hash,
                    "A página vigiada mudou", watch.url);
        }
        watch.lastHash = hash;
        watch.status = "OK";
        watches.save(watch);
        if (changed) {
            process(contest, watch.url, page, hash, count);
        }
        if (!isPdf(page)) {
            int fresh = 0;
            for (URI link : pdfLinks(uri, new String(page, StandardCharsets.UTF_8))) {
                if (fresh >= MAX_NEW_DOCUMENTS || documents.existsByContestIdAndUrl(contest.id, link.toString())) {
                    continue;
                }
                fresh++;
                download(contest, link, count);
            }
        }
    }

    private void download(Contest contest, URI link, int[] count) {
        try {
            byte[] data = fetcher.fetchBytes(link, MAX_BYTES);
            process(contest, link.toString(), data, sha256(data), count);
        } catch (DomainException ex) {
            save(contest, link.toString(), sha256(link.toString().getBytes(StandardCharsets.UTF_8)), "FAILED");
            unread(contest, link.toString(), "O documento não pôde ser baixado.", count);
        }
    }

    private void process(Contest contest, String url, byte[] data, String hash, int[] count) {
        if (documents.existsByContestIdAndHash(contest.id, hash)) {
            return;
        }
        count[2]++;
        String text;
        try {
            text = isPdf(data) ? DocumentText.pdfText(data) : htmlText(new String(data, StandardCharsets.UTF_8));
        } catch (DomainException ex) {
            save(contest, url, hash, "FAILED");
            unread(contest, url, "O documento não pôde ser lido.", count);
            return;
        }
        if (text.isBlank()) {
            save(contest, url, hash, "NO_TEXT");
            unread(contest, url, "O documento não tem texto legível (provavelmente é uma imagem). Confira manualmente.", count);
            return;
        }
        save(contest, url, hash, "READ");
        for (ContestMatcher.Found found : ContestMatcher.find(text, contest.searchName, contest.registration)) {
            ContestMatch match = new ContestMatch();
            match.id = UUID.randomUUID();
            match.contestId = contest.id;
            match.url = url;
            match.kind = found.kind();
            match.confidence = found.confidence();
            match.line = found.line();
            match.foundAt = clock.instant();
            matches.save(match);
            count[3]++;
            boolean high = "HIGH".equals(found.confidence());
            notify(contest, NotificationType.ACTION_REQUIRED, NotificationPriority.HIGH, "match:" + hash + ":" + found.kind(),
                    high ? "Possível menção: número de inscrição encontrado" : "Possível menção: nome encontrado (pode ser homônimo)",
                    "Confira no documento oficial: " + url);
        }
    }

    private void save(Contest contest, String url, String hash, String status) {
        ContestDocument document = new ContestDocument();
        document.id = UUID.randomUUID();
        document.contestId = contest.id;
        document.url = url;
        document.hash = hash;
        document.status = status;
        document.readAt = clock.instant();
        documents.save(document);
    }

    private void unread(Contest contest, String url, String reason, int[] count) {
        count[4]++;
        notify(contest, NotificationType.SOURCE_FAILING, NotificationPriority.HIGH,
                "unread:" + sha256(url.getBytes(StandardCharsets.UTF_8)) + ":" + LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC),
                "Documento não lido: " + reason, url);
    }

    private void remindDeadlines(Contest contest) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (Stage stage : ContestJson.read(contest.stages)) {
            if (stage.deadline() == null || stage.status() == ContestDtos.StageStatus.DONE) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, stage.deadline());
            for (int lead : DEADLINE_DAYS) {
                if (days == lead) {
                    notify(contest, NotificationType.DEADLINE_REMINDER, NotificationPriority.HIGH,
                            "deadline:" + stage.title() + ":" + stage.deadline() + ":" + lead,
                            "Prazo em " + lead + " dia(s): " + stage.title(), null);
                }
            }
        }
    }

    private void notify(Contest contest, NotificationType type, NotificationPriority priority, String key, String message, String detail) {
        Instant now = clock.instant();
        notifications.record(contest.userId, new NotificationDraft(type.name(), type, priority,
                "contest:" + contest.id + ":" + key, null, null, null, contest.organ, contest.name, detail, message, now, null, null));
    }

    private boolean allowedByRobots(URI uri) {
        try {
            byte[] data = fetcher.fetchBytes(uri.resolve("/robots.txt"), 256 * 1024);
            return RobotsRules.allows(new String(data, StandardCharsets.UTF_8), uri.getRawPath());
        } catch (DomainException | NullPointerException ex) {
            return true; // Sem robots.txt legível, não há regra a respeitar.
        }
    }

    static List<URI> pdfLinks(URI base, String html) {
        Set<URI> links = new LinkedHashSet<>();
        Matcher matcher = HREF.matcher(html);
        while (matcher.find()) {
            try {
                URI link = base.resolve(matcher.group(1).strip().replace(" ", "%20"));
                if ("https".equals(link.getScheme()) || "http".equals(link.getScheme())) {
                    links.add(link);
                }
            } catch (IllegalArgumentException ex) {
                log.debug("Link inválido ignorado");
            }
        }
        return new ArrayList<>(links);
    }

    static String htmlText(String html) {
        String clean = HIDDEN.matcher(html).replaceAll(" ");
        clean = BLOCK.matcher(clean).replaceAll("\n");
        return TAG.matcher(clean).replaceAll(" ").replace("&nbsp;", " ").replace("&amp;", "&");
    }

    private static boolean isPdf(byte[] data) {
        return data.length > 4 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F';
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
