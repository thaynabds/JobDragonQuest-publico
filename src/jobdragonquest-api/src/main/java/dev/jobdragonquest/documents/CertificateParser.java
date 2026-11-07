package dev.jobdragonquest.documents;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê certificados e comprovantes de curso (texto já extraído) e monta uma proposta de item para o perfil: título,
 * emissor, data, carga horária, código e link de verificação. Só preenche o que achou; o resto fica vazio para a pessoa
 * completar. A proposta nunca é gravada sozinha.
 */
final class CertificateParser {

    /** Proposta de item. {@code kind}: CERTIFICATION ou COURSE (curso com carga horária). */
    record Proposal(String kind, String title, String organization, LocalDate date, Integer hours, String credentialId, String url,
            List<String> skills) {
    }

    private static final List<Pattern> TITLE = List.of(
            Pattern.compile("(?i)conclu[ií]u (?:com (?:êxito|exito|aproveitamento) )?o (?:curso|treinamento|programa|bootcamp)(?: de| online de)?\\s*[\"“]?([^\\n\"”]{3,150})"),
            Pattern.compile("(?i)(?:curso|treinamento|certifica[cç][aã]o)(?: de| em)?\\s*[:\\-]\\s*([^\\n]{3,150})"),
            Pattern.compile("(?i)(?:has successfully )?completed(?: the)?(?: course| program| training)?\\s*[\"“]?([^\\n\"”]{3,150})"),
            Pattern.compile("(?i)complet[oó] (?:el|la) (?:curso|programa|formaci[oó]n)(?: de)?\\s*([^\\n]{3,150})"));
    private static final Pattern HOURS = Pattern.compile("(?i)(\\d{1,4})\\s*(?:horas?|hrs?|h|hours?)(?![a-z])");
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)(?:c[oó]digo|credencial|credential(?: id)?|certificado n[º°o.]*|certificate (?:no\\.?|number|id)|id)\\s*[:#]?\\s*([A-Z0-9][A-Z0-9\\-]{5,60})");
    private static final Pattern UDEMY = Pattern.compile("\\b(UC-[A-Za-z0-9\\-]{8,60})\\b");
    private static final Pattern URL = Pattern.compile("https?://[^\\s\"'<>]{4,300}");
    private static final Pattern DATE_BR = Pattern.compile("\\b(\\d{2}/\\d{2}/\\d{4})\\b");
    private static final Pattern DATE_ISO = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern DATE_EN = Pattern.compile("(?i)\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.? (\\d{1,2}),? (\\d{4})\\b");
    private static final Map<String, String> ISSUERS = Map.ofEntries(
            Map.entry("udemy", "Udemy"), Map.entry("alura", "Alura"), Map.entry("coursera", "Coursera"), Map.entry("dio", "DIO"),
            Map.entry("fiap", "FIAP"), Map.entry("rocketseat", "Rocketseat"), Map.entry("fundação bradesco", "Fundação Bradesco"),
            Map.entry("senai", "SENAI"), Map.entry("sebrae", "Sebrae"), Map.entry("google", "Google"), Map.entry("microsoft", "Microsoft"),
            Map.entry("aws", "Amazon Web Services"), Map.entry("oracle", "Oracle"), Map.entry("linkedin learning", "LinkedIn Learning"),
            Map.entry("edx", "edX"), Map.entry("cisco", "Cisco"), Map.entry("ibm", "IBM"), Map.entry("santander", "Santander"),
            Map.entry("escola virtual", "Escola Virtual"), Map.entry("freecodecamp", "freeCodeCamp"), Map.entry("credly", "Credly"));

    private CertificateParser() {
    }

    static Proposal parse(String text, List<String> skills) {
        String t = text == null ? "" : text;
        String title = null;
        for (Pattern pattern : TITLE) {
            Matcher matcher = pattern.matcher(t);
            if (matcher.find()) {
                title = tidy(matcher.group(1));
                if (title != null) {
                    break;
                }
            }
        }
        String lower = t.toLowerCase(Locale.ROOT);
        String issuer = ISSUERS.entrySet().stream()
                .filter(e -> Pattern.compile("(?<![\\p{L}])" + Pattern.quote(e.getKey()) + "(?![\\p{L}])").matcher(lower).find())
                .map(Map.Entry::getValue).sorted().findFirst().orElse(null);
        Matcher hours = HOURS.matcher(t);
        Integer workload = null;
        if (hours.find()) {
            int value = Integer.parseInt(hours.group(1));
            workload = value > 0 && value <= 5000 ? value : null;
        }
        Matcher udemy = UDEMY.matcher(t);
        Matcher credential = CREDENTIAL.matcher(t);
        String code = udemy.find() ? udemy.group(1) : credential.find() ? credential.group(1) : null;
        Matcher url = URL.matcher(t);
        String link = url.find() ? url.group().replaceAll("[).,;]+$", "") : null;
        return new Proposal(workload != null ? "COURSE" : "CERTIFICATION", title, issuer, date(t), workload, code, link, skills);
    }

    /** A data mais recente encontrada (a de conclusão costuma ser a última). */
    static LocalDate date(String text) {
        LocalDate best = null;
        Matcher br = DATE_BR.matcher(text);
        while (br.find()) {
            best = latest(best, parse(br.group(1), DateTimeFormatter.ofPattern("dd/MM/uuuu")));
        }
        Matcher iso = DATE_ISO.matcher(text);
        while (iso.find()) {
            best = latest(best, parse(iso.group(1), DateTimeFormatter.ofPattern("uuuu-MM-dd")));
        }
        Matcher en = DATE_EN.matcher(text);
        while (en.find()) {
            best = latest(best, parse(en.group(1).substring(0, 3) + " " + en.group(2) + " " + en.group(3),
                    DateTimeFormatter.ofPattern("MMM d uuuu", Locale.ENGLISH)));
        }
        return best;
    }

    private static LocalDate parse(String value, DateTimeFormatter format) {
        try {
            String normalized = value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
            LocalDate date = LocalDate.parse(Character.isLetter(value.charAt(0)) ? normalized : value, format.withResolverStyle(ResolverStyle.STRICT));
            return date.isAfter(LocalDate.now().plusDays(1)) || date.getYear() < 1950 ? null : date;
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static LocalDate latest(LocalDate a, LocalDate b) {
        return a == null ? b : b == null ? a : (b.isAfter(a) ? b : a);
    }

    private static String tidy(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").replaceAll("[\"”,.;:]+$", "").strip();
        cleaned = cleaned.replaceAll("(?i)\\s+(com carga hor[aá]ria|with a duration|en la fecha|no dia|em \\d).*$", "").strip();
        return cleaned.length() < 3 ? null : cleaned.substring(0, Math.min(cleaned.length(), 200));
    }
}
