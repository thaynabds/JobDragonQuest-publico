package dev.jobdragonquest.sources.collect;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.web.util.HtmlUtils;

import dev.jobdragonquest.common.domain.WorkMode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Utilidades compartilhadas pelos adaptadores: JSON tolerante, texto sem HTML e inferência de modalidade. */
final class AtsParsing {

    static final int MAX_JOBS = 500;
    static final int MAX_DESCRIPTION = 20_000;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n+");

    private AtsParsing() {
    }

    static JsonNode read(byte[] body) {
        try {
            return JSON.readTree(body);
        } catch (JacksonException ex) {
            throw new CollectionException("O ATS devolveu um JSON inválido", ex);
        }
    }

    /** Texto do campo, ou null se ausente ou vazio. */
    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asString().strip();
        return text.isEmpty() ? null : text;
    }

    /**
     * Converte HTML (às vezes já escapado, como no Greenhouse) em texto simples. O resultado é exibido como texto no
     * painel, nunca como HTML.
     */
    static String plain(String html) {
        if (html == null) {
            return null;
        }
        String unescaped = HtmlUtils.htmlUnescape(html);
        String withBreaks = unescaped.replaceAll("(?i)<\\s*(br|/p|/li|/h[1-6]|/div)\\s*/?>", "\n");
        String text = HtmlUtils.htmlUnescape(TAGS.matcher(withBreaks).replaceAll(" "));
        text = SPACES.matcher(text).replaceAll(" ");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n").strip();
        return text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION) : text;
    }

    /** Modalidade a partir de sinais explícitos do ATS ou do texto da localização. Sem sinal, null (não inventa). */
    static WorkMode workMode(String explicit, Boolean remoteFlag, String location) {
        if (explicit != null) {
            String value = explicit.toLowerCase(Locale.ROOT).replace("_", "-");
            if (value.contains("remote")) {
                return WorkMode.REMOTE;
            }
            if (value.contains("hybrid")) {
                return WorkMode.HYBRID;
            }
            if (value.contains("on-site") || value.contains("onsite") || value.contains("in-office")) {
                return WorkMode.ONSITE;
            }
        }
        if (Boolean.TRUE.equals(remoteFlag)) {
            return WorkMode.REMOTE;
        }
        if (location != null) {
            String value = location.toLowerCase(Locale.ROOT);
            if (value.contains("remote") || value.contains("remoto") || value.contains("anywhere")) {
                return WorkMode.REMOTE;
            }
            if (value.contains("hybrid") || value.contains("híbrido") || value.contains("hibrido")) {
                return WorkMode.HYBRID;
            }
        }
        return null;
    }

    static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() > max ? stripped.substring(0, max) : stripped;
    }
}
