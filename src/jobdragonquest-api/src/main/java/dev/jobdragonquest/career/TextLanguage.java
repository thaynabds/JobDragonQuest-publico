package dev.jobdragonquest.career;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Idioma provável de um texto de vaga (pt, en ou es), pela contagem de palavras muito comuns. Sem certeza, devolve null. */
final class TextLanguage {

    private static final Map<String, Set<String>> WORDS = Map.of(
            "pt", Set.of("de", "para", "com", "uma", "você", "não", "em", "os", "as", "experiência", "vaga", "conhecimento", "são", "ao", "dos", "das"),
            "en", Set.of("the", "and", "with", "you", "for", "our", "will", "of", "to", "experience", "are", "this", "your", "we", "in", "skills"),
            "es", Set.of("el", "la", "los", "las", "con", "para", "una", "experiencia", "conocimientos", "que", "y", "del", "nuestro", "puesto", "trabajo", "buscamos"));

    private TextLanguage() {
    }

    static String guess(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Map<String, Integer> score = new java.util.HashMap<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) {
            WORDS.forEach((language, words) -> {
                if (words.contains(word)) {
                    score.merge(language, 1, Integer::sum);
                }
            });
        }
        var best = score.entrySet().stream().max(Map.Entry.comparingByValue());
        if (best.isEmpty() || best.get().getValue() < 3) {
            return null;
        }
        long ties = score.values().stream().filter(best.get().getValue()::equals).count();
        return ties > 1 ? null : best.get().getKey();
    }
}
