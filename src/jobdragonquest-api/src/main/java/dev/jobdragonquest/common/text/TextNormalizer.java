package dev.jobdragonquest.common.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normaliza textos para comparação: minúsculas, sem acento, sem pontuação de borda e com espaços simples.
 * Usada na deduplicação de vagas e candidaturas e na comparação de localidades.
 */
public final class TextNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}+#./ ]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    // Sufixos societários que não distinguem empresas ("Avanade Brasil Ltda" = "Avanade Brasil").
    private static final Pattern COMPANY_SUFFIX = Pattern.compile(
            "\\b(ltda|s\\.?a\\.?|inc\\.?|llc|ltd\\.?|gmbh|corp\\.?|corporation|eireli|me|epp)$");

    private TextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String plain = DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
        String cleaned = NON_WORD.matcher(plain).replaceAll(" ");
        return SPACES.matcher(cleaned).replaceAll(" ").trim();
    }

    public static String normalizeCompany(String company) {
        String normalized = normalize(company);
        String previous;
        do {
            previous = normalized;
            normalized = COMPANY_SUFFIX.matcher(normalized).replaceAll("").trim();
        } while (!normalized.equals(previous) && !normalized.isEmpty());
        return normalized.isEmpty() ? normalize(company) : normalized;
    }
}
