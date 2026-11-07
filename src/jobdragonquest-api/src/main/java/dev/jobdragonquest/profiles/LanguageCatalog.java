package dev.jobdragonquest.profiles;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converte o nome de um idioma, digitado por extenso, no código ISO (639-1, ou 639-3 quando não há de duas letras).
 * Aceita o nome em português, inglês, espanhol ou no próprio idioma, sem diferenciar acento e maiúsculas, a partir
 * dos dados de localidade do próprio Java. Também aceita o código direto.
 */
public final class LanguageCatalog {

    private static final Pattern CODE = Pattern.compile("[a-z]{2,3}");
    private static final Locale[] DISPLAY = {Locale.of("pt", "BR"), Locale.ENGLISH, Locale.of("es")};
    /** Idiomas sem código de duas letras que aparecem em perfis brasileiros. */
    private static final Map<String, String> EXTRA = Map.of(
            "libras", "bzs",
            "lingua brasileira de sinais", "bzs",
            "brazilian sign language", "bzs",
            "lengua de senas brasilena", "bzs",
            "asl", "ase",
            "american sign language", "ase",
            "lingua de sinais americana", "ase");
    private static final Set<String> EXTRA_CODES = Set.of("bzs", "ase");
    private static final Map<String, String> BY_NAME = buildIndex();

    private LanguageCatalog() {
    }

    public static Optional<String> resolve(String nameOrCode) {
        if (nameOrCode == null) {
            return Optional.empty();
        }
        String raw = nameOrCode.strip();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (CODE.matcher(lower).matches() && (isIsoLanguage(lower) || EXTRA_CODES.contains(lower))) {
            return Optional.of(lower);
        }
        return Optional.ofNullable(BY_NAME.get(normalize(raw)));
    }

    static String normalize(String value) {
        String stripped = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    private static boolean isIsoLanguage(String code) {
        for (String iso : Locale.getISOLanguages()) {
            if (iso.equals(code)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> buildIndex() {
        Map<String, String> index = new HashMap<>();
        for (String code : Locale.getISOLanguages()) {
            Locale language = Locale.of(code);
            for (Locale display : DISPLAY) {
                put(index, language.getDisplayLanguage(display), code);
            }
            put(index, language.getDisplayLanguage(language), code);
        }
        EXTRA.forEach((name, code) -> index.put(normalize(name), code));
        return Map.copyOf(index);
    }

    private static void put(Map<String, String> index, String name, String code) {
        if (name == null || name.isBlank() || name.equalsIgnoreCase(code)) {
            return;
        }
        index.putIfAbsent(normalize(name), code);
    }
}
