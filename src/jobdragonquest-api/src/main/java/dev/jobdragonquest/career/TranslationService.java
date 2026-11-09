package dev.jobdragonquest.career;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import dev.jobdragonquest.sources.collect.BoundedHttpFetcher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tradução automática do português para o idioma da vaga pelo serviço gratuito MyMemory. Só roda quando a pessoa liga a
 * opção no perfil, porque o texto sai do servidor. Vai só texto de resumo e de experiência, nunca contato ou documento.
 * Falhou ou passou do limite diário do serviço: devolve null e o texto original continua, com aviso para a pessoa.
 */
@Service
public class TranslationService {

    private static final int MAX_CHUNK = 450;
    private static final int MAX_CACHE = 2000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final BoundedHttpFetcher fetcher;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    TranslationService(BoundedHttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public String translate(String text, String target) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String key = target + "|" + text;
        String known = cache.get(key);
        if (known != null) {
            return known;
        }
        StringBuilder out = new StringBuilder();
        for (String chunk : chunks(text)) {
            String piece = chunk.isBlank() ? chunk : remote(chunk, target);
            if (piece == null) {
                return null;
            }
            out.append(piece);
        }
        if (cache.size() >= MAX_CACHE) {
            cache.clear();
        }
        cache.put(key, out.toString());
        return out.toString();
    }

    private String remote(String chunk, String target) {
        try {
            String query = "q=" + URLEncoder.encode(chunk.strip(), StandardCharsets.UTF_8) + "&langpair=" + URLEncoder.encode("pt-BR|" + target, StandardCharsets.UTF_8);
            JsonNode root = JSON.readTree(fetcher.get(URI.create("https://api.mymemory.translated.net/get?" + query)));
            if (root.path("responseStatus").asInt(0) != 200) {
                return null;
            }
            String translated = root.path("responseData").path("translatedText").asString("");
            if (translated.isBlank()) {
                return null;
            }
            String lead = chunk.substring(0, chunk.length() - chunk.stripLeading().length());
            String tail = chunk.substring(chunk.stripTrailing().length());
            return lead + HtmlUtils.htmlUnescape(translated) + tail;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Pedaços de até 450 caracteres, cortados em fim de frase ou espaço, mantendo as quebras de linha. */
    static List<String> chunks(String text) {
        List<String> result = new ArrayList<>();
        String rest = text;
        while (rest.length() > MAX_CHUNK) {
            int cut = lastBreak(rest);
            result.add(rest.substring(0, cut));
            rest = rest.substring(cut);
        }
        result.add(rest);
        return result;
    }

    private static int lastBreak(String text) {
        String window = text.substring(0, MAX_CHUNK);
        int cut = Math.max(window.lastIndexOf(". "), window.lastIndexOf('\n'));
        if (cut > 0) {
            return cut + 1;
        }
        cut = window.lastIndexOf(' ');
        return cut > 0 ? cut + 1 : MAX_CHUNK;
    }
}
