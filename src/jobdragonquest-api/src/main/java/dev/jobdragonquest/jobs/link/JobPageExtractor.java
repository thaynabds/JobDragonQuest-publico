package dev.jobdragonquest.jobs.link;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.HtmlUtils;

import dev.jobdragonquest.common.domain.WorkMode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tira os dados de uma vaga do HTML de uma página: primeiro do JSON-LD {@code JobPosting} (schema.org, que a maioria
 * dos sites de vaga publica para buscadores) e, sem ele, das metatags do título e da descrição. Não inventa: campo
 * ausente fica vazio para a pessoa completar. O HTML é dado não confiável e vira só texto.
 */
final class JobPageExtractor {

    static final int MAX_BLOCKS = 20;
    static final int MAX_DESCRIPTION = 20_000;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern LD_JSON = Pattern.compile(
            "<script[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n+");

    /** Dados extraídos. {@code from}: JSON_LD ou META. */
    record Extracted(String from, String title, String company, String description, String location, String country,
            WorkMode workMode, BigDecimal salaryMin, BigDecimal salaryMax, String salaryCurrency, String salaryPeriod) {
    }

    private JobPageExtractor() {
    }

    static Extracted extract(String html, URI page) {
        return fromJsonLd(html).orElseGet(() -> fromMeta(html, page));
    }

    private static Optional<Extracted> fromJsonLd(String html) {
        Matcher matcher = LD_JSON.matcher(html);
        int blocks = 0;
        while (matcher.find() && blocks++ < MAX_BLOCKS) {
            JsonNode root;
            try {
                root = JSON.readTree(matcher.group(1).strip());
            } catch (JacksonException ex) {
                continue;
            }
            JsonNode posting = findPosting(root, 0);
            if (posting != null) {
                return Optional.of(toExtracted(posting));
            }
        }
        return Optional.empty();
    }

    /** Procura o nó JobPosting no JSON-LD (direto, em lista ou em @graph), sem descer demais. */
    private static JsonNode findPosting(JsonNode node, int depth) {
        if (node == null || depth > 4) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode found = findPosting(item, depth + 1);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        JsonNode type = node.path("@type");
        boolean posting = type.isArray() ? stream(type).anyMatch(t -> "JobPosting".equals(t.asString()))
                : "JobPosting".equals(type.asString());
        return posting ? node : findPosting(node.path("@graph").isMissingNode() ? null : node.path("@graph"), depth + 1);
    }

    private static Extracted toExtracted(JsonNode posting) {
        String title = clean(text(posting, "title"), 200);
        JsonNode org = posting.path("hiringOrganization");
        String company = clean(org.isObject() ? text(org, "name") : nodeText(org), 200);
        String description = plain(text(posting, "description"));
        String locality = null;
        String region = null;
        String country = null;
        JsonNode location = posting.path("jobLocation");
        JsonNode first = location.isArray() && !location.isEmpty() ? location.get(0) : location;
        JsonNode address = first.path("address");
        if (address.isObject()) {
            locality = text(address, "addressLocality");
            region = text(address, "addressRegion");
            JsonNode countryNode = address.path("addressCountry");
            country = countryNode.isObject() ? text(countryNode, "name") : nodeText(countryNode);
        }
        List<String> parts = new ArrayList<>();
        if (locality != null) {
            parts.add(locality);
        }
        if (region != null) {
            parts.add(region);
        }
        String iso = country != null && country.strip().matches("[A-Za-z]{2}") ? country.strip().toUpperCase(Locale.ROOT) : null;
        if (iso == null && country != null) {
            parts.add(country);
        }
        WorkMode mode = "TELECOMMUTE".equalsIgnoreCase(text(posting, "jobLocationType")) ? WorkMode.REMOTE : null;

        BigDecimal min = null;
        BigDecimal max = null;
        String currency = null;
        String period = null;
        JsonNode salary = posting.path("baseSalary");
        if (salary.isObject()) {
            currency = text(salary, "currency");
            JsonNode value = salary.path("value");
            if (value.isObject()) {
                min = number(value.path("minValue"));
                max = number(value.path("maxValue"));
                if (min == null && max == null) {
                    min = number(value.path("value"));
                    max = min;
                }
                period = switch (String.valueOf(text(value, "unitText")).toUpperCase(Locale.ROOT)) {
                    case "HOUR" -> "HOUR";
                    case "MONTH" -> "MONTH";
                    case "YEAR" -> "YEAR";
                    default -> null;
                };
            } else {
                min = number(value);
                max = min;
            }
        }
        boolean salaryOk = currency != null && currency.matches("[A-Za-z]{3}") && (min != null || max != null)
                && (min == null || max == null || min.compareTo(max) <= 0);
        return new Extracted("JSON_LD", title, company, description, parts.isEmpty() ? null : clean(String.join(", ", parts), 200),
                iso, mode, salaryOk ? min : null, salaryOk ? max : null, salaryOk ? currency.toUpperCase(Locale.ROOT) : null,
                salaryOk ? period : null);
    }

    private static Extracted fromMeta(String html, URI page) {
        String title = meta(html, "og:title");
        if (title == null) {
            Matcher matcher = TITLE.matcher(html);
            title = matcher.find() ? plain(matcher.group(1)) : null;
        }
        String company = meta(html, "og:site_name");
        if (company == null && page.getHost() != null) {
            company = page.getHost().replaceFirst("^www\\.", "");
        }
        String description = meta(html, "og:description");
        if (description == null) {
            description = meta(html, "description");
        }
        return new Extracted("META", clean(title, 200), clean(company, 200), plain(description), null, null, null, null, null, null, null);
    }

    /** Conteúdo de uma metatag (property ou name), em qualquer ordem dos atributos. */
    private static String meta(String html, String key) {
        Pattern pattern = Pattern.compile("<meta[^>]+(?:property|name)\\s*=\\s*[\"']" + Pattern.quote(key) + "[\"'][^>]*>",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        Matcher content = Pattern.compile("content\\s*=\\s*\"([^\"]*)\"|content\\s*=\\s*'([^']*)'", Pattern.CASE_INSENSITIVE)
                .matcher(matcher.group());
        if (!content.find()) {
            return null;
        }
        String value = content.group(1) != null ? content.group(1) : content.group(2);
        return plain(value);
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
        List<JsonNode> items = new ArrayList<>();
        array.forEach(items::add);
        return items.stream();
    }

    private static String text(JsonNode node, String field) {
        return nodeText(node.path(field));
    }

    private static String nodeText(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull() || value.isContainer()) {
            return null;
        }
        String text = value.asString().strip();
        return text.isEmpty() ? null : text;
    }

    private static BigDecimal number(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        try {
            BigDecimal number = new BigDecimal(value.asString().strip());
            return number.signum() < 0 || number.compareTo(new BigDecimal("9999999999")) > 0 ? null : number;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** HTML vira texto simples (o painel exibe sempre como texto). */
    static String plain(String html) {
        if (html == null) {
            return null;
        }
        String unescaped = HtmlUtils.htmlUnescape(html);
        String withBreaks = unescaped.replaceAll("(?i)<\\s*(br|/p|/li|/h[1-6]|/div)\\s*/?>", "\n");
        String text = HtmlUtils.htmlUnescape(TAGS.matcher(withBreaks).replaceAll(" "));
        text = SPACES.matcher(text).replaceAll(" ");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n").strip();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION) : text;
    }

    private static String clean(String value, int max) {
        String text = plain(value);
        if (text == null) {
            return null;
        }
        text = text.replace('\n', ' ').strip();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
