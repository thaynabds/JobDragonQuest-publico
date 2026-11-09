package dev.jobdragonquest.contests;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import dev.jobdragonquest.common.text.TextNormalizer;

/**
 * Procura o número de inscrição e o nome da pessoa num texto oficial, linha a linha, sem acento e sem diferença de
 * maiúsculas. Guarda só o trecho da correspondência, porque listas públicas citam outras pessoas.
 */
final class ContestMatcher {

    static final int MAX_LINE = 200;

    record Found(String kind, String confidence, String line) {
    }

    private ContestMatcher() {
    }

    static List<Found> find(String text, String name, String registration) {
        List<Found> found = new ArrayList<>();
        Pattern byRegistration = word(registration);
        Pattern byName = word(name);
        for (String raw : text.split("\\R")) {
            String line = TextNormalizer.normalize(raw);
            if (byRegistration != null && byRegistration.matcher(line).find()) {
                found.add(new Found("REGISTRATION", "HIGH", excerpt(line, byRegistration)));
            } else if (byName != null && byName.matcher(line).find()) {
                found.add(new Found("NAME", "MEDIUM", excerpt(line, byName)));
            }
        }
        return found;
    }

    private static Pattern word(String value) {
        String needle = TextNormalizer.normalize(value);
        if (needle.isEmpty()) {
            return null;
        }
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(needle) + "(?![\\p{L}\\p{N}])");
    }

    private static String excerpt(String line, Pattern pattern) {
        var matcher = pattern.matcher(line);
        matcher.find();
        int start = Math.max(0, matcher.start() - 40);
        int end = Math.min(line.length(), Math.min(matcher.end() + 60, start + MAX_LINE));
        return line.substring(start, end);
    }
}
