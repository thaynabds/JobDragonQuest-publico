package dev.jobdragonquest.contests;

import java.util.ArrayList;
import java.util.List;

/** Leitura simples do robots.txt: regras Disallow do grupo "*", por prefixo do caminho. */
final class RobotsRules {

    private RobotsRules() {
    }

    static boolean allows(String robots, String path) {
        String target = path == null || path.isEmpty() ? "/" : path;
        List<String> blocked = new ArrayList<>();
        boolean general = false;
        for (String raw : robots.split("\\R")) {
            String line = raw.replaceFirst("#.*", "").strip();
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).strip().toLowerCase(java.util.Locale.ROOT);
            String value = line.substring(colon + 1).strip();
            if (key.equals("user-agent")) {
                general = value.equals("*");
            } else if (general && key.equals("disallow") && !value.isEmpty()) {
                blocked.add(value);
            }
        }
        return blocked.stream().noneMatch(target::startsWith);
    }
}
