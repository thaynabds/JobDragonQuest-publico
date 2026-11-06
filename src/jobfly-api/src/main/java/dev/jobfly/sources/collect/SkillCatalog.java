package dev.jobfly.sources.collect;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import dev.jobfly.common.text.SkillNormalizer;
import dev.jobfly.common.text.TextNormalizer;

/**
 * Reconhece tecnologias citadas no texto de uma vaga coletada, como palavra inteira ("java" não casa dentro de
 * "javascript"). É um recurso explícito e determinístico até a camada de IA existir: o resultado vira requisitos
 * marcados como "mencionado na descrição", e a análise não os trata como obrigatórios.
 */
@Component
public class SkillCatalog {

    private final Map<String, Pattern> patterns;

    public SkillCatalog() {
        this(load());
    }

    SkillCatalog(List<String> skills) {
        // Nomes mais longos primeiro: em "spring boot" aparecem "spring boot" e também "spring", e os dois contam.
        this.patterns = skills.stream()
                .map(SkillNormalizer::normalize)
                .filter(skill -> !skill.isBlank())
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                .collect(Collectors.toMap(skill -> skill,
                        skill -> Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(skill) + "(?![\\p{L}\\p{N}+#])"),
                        (a, b) -> a, LinkedHashMap::new));
    }

    /** Tecnologias canônicas citadas no texto, na ordem do catálogo (mais longas primeiro). */
    public Set<String> detect(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String normalized = " " + TextNormalizer.normalize(text) + " ";
        Set<String> found = new LinkedHashSet<>();
        patterns.forEach((skill, pattern) -> {
            if (pattern.matcher(normalized).find()) {
                found.add(skill);
            }
        });
        return found;
    }

    private static List<String> load() {
        ClassPathResource resource = new ClassPathResource("skills-catalog.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("Catálogo de competências ausente", ex);
        }
    }
}
