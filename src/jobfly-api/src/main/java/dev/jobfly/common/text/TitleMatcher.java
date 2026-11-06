package dev.jobfly.common.text;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Compara cargos palavra a palavra, tolerando flexão de gênero e número do português ("Desenvolvedora" casa com
 * "Desenvolvedor", "Estagiária" com "Estagiário") e ignorando preposições. Usado na afinidade de cargo da análise de
 * compatibilidade e no filtro de palavras-chave da coleta de vagas.
 */
public final class TitleMatcher {

    private static final int MIN_STEM_LENGTH = 5;
    private static final Set<String> STOP_WORDS = Set.of("de", "da", "do", "das", "dos", "em", "e", "para", "com", "a", "o",
            "of", "the", "and", "in", "for", "-", "/");

    private TitleMatcher() {
    }

    /** O texto contém todas as palavras significativas da frase (frase sem palavras significativas nunca casa). */
    public static boolean containsAllWords(String text, String phrase) {
        List<String> wanted = words(phrase);
        if (wanted.isEmpty()) {
            return false;
        }
        List<String> available = words(text);
        return wanted.stream().allMatch(word -> available.stream().anyMatch(candidate -> sameWord(word, candidate)));
    }

    static List<String> words(String text) {
        return Arrays.stream(TextNormalizer.normalize(text).split(" "))
                .filter(word -> !word.isBlank() && !STOP_WORDS.contains(word))
                .toList();
    }

    static boolean sameWord(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        if (a.length() < MIN_STEM_LENGTH || b.length() < MIN_STEM_LENGTH) {
            return false;
        }
        String stemA = stem(a);
        String stemB = stem(b);
        return stemA.equals(stemB) || stemA.startsWith(stemB) || stemB.startsWith(stemA);
    }

    /** Radical simples para flexão de gênero e número do português (a/o/as/os/es finais). */
    private static String stem(String word) {
        return word.replaceFirst("(as|os|es|a|o|s)$", "");
    }
}
