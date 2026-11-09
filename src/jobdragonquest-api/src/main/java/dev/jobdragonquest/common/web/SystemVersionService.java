package dev.jobdragonquest.common.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import dev.jobdragonquest.sources.collect.BoundedHttpFetcher;

/**
 * Compara a versão em execução com a última Release publicada. Consulta um endereço fixo no máximo a cada 6 horas e
 * nunca derruba a tela: se o GitHub falhar, a versão mais recente fica desconhecida.
 */
@Service
public class SystemVersionService {

    static final URI LATEST = URI.create("https://api.github.com/repos/thaynabds/JobDragonQuest-publico/releases/latest");
    private static final String RELEASES = "https://github.com/thaynabds/JobDragonQuest-publico/releases/tag/v";
    private static final Duration CACHE = Duration.ofHours(6);
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"v(\\d{1,4}\\.\\d{1,4}\\.\\d{1,4})\"");

    public record VersionInfo(String current, String latest, boolean updateAvailable, String releaseUrl) {
    }

    private final BoundedHttpFetcher fetcher;
    private final Clock clock;
    private final String current;
    private volatile String latest;
    private volatile Instant checkedAt;

    @Autowired
    SystemVersionService(BoundedHttpFetcher fetcher, Clock clock, ObjectProvider<BuildProperties> build) {
        this(fetcher, clock, build.stream().map(BuildProperties::getVersion).findFirst().orElse(null));
    }

    SystemVersionService(BoundedHttpFetcher fetcher, Clock clock, String current) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.current = current;
    }

    public VersionInfo info() {
        refresh();
        String known = latest;
        boolean newer = current != null && known != null && isNewer(known, current);
        return new VersionInfo(current, known, newer, known == null ? null : RELEASES + known);
    }

    private synchronized void refresh() {
        Instant now = clock.instant();
        if (checkedAt != null && checkedAt.plus(CACHE).isAfter(now)) {
            return;
        }
        checkedAt = now;
        try {
            Matcher matcher = TAG.matcher(new String(fetcher.get(LATEST), StandardCharsets.UTF_8));
            if (matcher.find()) {
                latest = matcher.group(1);
            }
        } catch (RuntimeException ex) {
            // Sem conexão ou limite do GitHub: mantém o último valor conhecido e tenta de novo no próximo ciclo.
        }
    }

    static boolean isNewer(String candidate, String base) {
        String[] a = candidate.split("[.-]");
        String[] b = base.split("[.-]");
        for (int i = 0; i < 3; i++) {
            int left = number(a, i);
            int right = number(b, i);
            if (left != right) {
                return left > right;
            }
        }
        return false;
    }

    private static int number(String[] parts, int index) {
        try {
            return index < parts.length ? Integer.parseInt(parts[index]) : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
