package dev.jobdragonquest.common.text;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.jobdragonquest.common.error.DomainException;

/**
 * Valida e canonicaliza URLs de vagas. Só aceita http e https: {@code javascript:}, {@code data:} e {@code file:}
 * são recusados, porque viram links clicáveis no painel. Parâmetros de rastreio e fragmentos não identificam a vaga
 * e por isso são removidos. A URL canônica dá a chave de deduplicação.
 *
 * <p>Esta classe não busca a URL na rede. Quando a busca de páginas existir, ela precisa de proteção contra SSRF
 * (ver docs/06-seguranca.md).
 */
public final class UrlCanonicalizer {

    public static final int MAX_LENGTH = 2048;

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "gclid", "fbclid", "mc_cid", "mc_eid", "trk", "trackingid", "refid", "_hsenc", "_hsmi",
            "igshid", "lipi", "si", "ref", "referer", "src");
    private static final Pattern LINKEDIN_JOB = Pattern.compile("^/jobs/view/(?:[^/]*-)?(\\d+)/?$");
    private static final Pattern LINKEDIN_CURRENT_JOB = Pattern.compile("(?:^|&)currentJobId=(\\d+)");

    private UrlCanonicalizer() {
    }

    /** Devolve a URL limpa ou lança {@link DomainException} se não for http(s) válida. */
    public static String canonicalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid();
        }
        String trimmed = raw.strip();
        if (trimmed.length() > MAX_LENGTH) {
            throw invalid();
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException ex) {
            throw invalid();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw invalid();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String query = uri.getRawQuery();

        if (host.endsWith("linkedin.com")) {
            Optional<String> jobId = linkedInJobId(path, query);
            if (jobId.isPresent()) {
                return "https://linkedin.com/jobs/view/" + jobId.get();
            }
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        int port = uri.getPort();
        boolean defaultPort = port == -1 || (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
        String cleanQuery = cleanQuery(query);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port) + path + (cleanQuery.isEmpty() ? "" : "?" + cleanQuery);
    }

    /** Hash SHA-256 da URL canônica (64 caracteres hexadecimais), usado nos índices de deduplicação. */
    public static String hash(String canonicalUrl) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }

    /** Para URLs opcionais: null ou vazia passa direto; se vier preenchida, precisa ser http(s) válida. */
    public static String optional(String raw) {
        return raw == null || raw.isBlank() ? null : canonicalize(raw);
    }

    private static Optional<String> linkedInJobId(String path, String query) {
        Matcher byPath = LINKEDIN_JOB.matcher(path);
        if (byPath.matches()) {
            return Optional.of(byPath.group(1));
        }
        if (query != null) {
            Matcher byQuery = LINKEDIN_CURRENT_JOB.matcher(query);
            if (byQuery.find()) {
                return Optional.of(byQuery.group(1));
            }
        }
        return Optional.empty();
    }

    private static String cleanQuery(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        return Arrays.stream(query.split("&"))
                .filter(part -> !part.isEmpty())
                .filter(part -> {
                    String name = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
                    return !name.startsWith("utm_") && !TRACKING_PARAMS.contains(name);
                })
                .sorted()
                .collect(Collectors.joining("&"));
    }

    private static DomainException invalid() {
        return DomainException.badRequest("invalid_url", "Informe um endereço http ou https válido");
    }
}
