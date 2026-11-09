package dev.jobdragonquest.sources.collect;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Identificador do board como um único segmento de caminho: barra, "?", "#", "@" e emojis vão codificados. */
public final class AtsPaths {

    private AtsPaths() {
    }

    public static String segment(String identifier) {
        return URLEncoder.encode(identifier, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
