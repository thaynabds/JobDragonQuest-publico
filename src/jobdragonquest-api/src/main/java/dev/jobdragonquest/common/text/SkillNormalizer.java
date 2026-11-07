package dev.jobdragonquest.common.text;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Forma canônica de uma competência, para "JS", "javascript" e "Java Script" contarem como a mesma coisa.
 * A lista é conservadora: só junta grafias da mesma tecnologia, nunca tecnologias diferentes
 * (Java ≠ JavaScript, Spring ≠ Spring Boot).
 */
public final class SkillNormalizer {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("js", "javascript"),
            Map.entry("java script", "javascript"),
            Map.entry("ecmascript", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("node", "node.js"),
            Map.entry("nodejs", "node.js"),
            Map.entry("node js", "node.js"),
            Map.entry("reactjs", "react"),
            Map.entry("react.js", "react"),
            Map.entry("react js", "react"),
            Map.entry("vuejs", "vue"),
            Map.entry("vue.js", "vue"),
            Map.entry("angular.js", "angularjs"),
            Map.entry("springboot", "spring boot"),
            Map.entry("spring-boot", "spring boot"),
            Map.entry("c#", "csharp"),
            Map.entry("c sharp", "csharp"),
            Map.entry(".net", "dotnet"),
            Map.entry("net", "dotnet"),
            Map.entry(".net core", "dotnet"),
            Map.entry("asp.net", "asp.net"),
            Map.entry("postgres", "postgresql"),
            Map.entry("postgre", "postgresql"),
            Map.entry("py", "python"),
            Map.entry("python3", "python"),
            Map.entry("k8s", "kubernetes"),
            Map.entry("golang", "go"),
            Map.entry("ms sql", "sql server"),
            Map.entry("mssql", "sql server"),
            Map.entry("amazon web services", "aws"),
            Map.entry("google cloud", "gcp"),
            Map.entry("google cloud platform", "gcp"),
            Map.entry("microsoft azure", "azure"),
            Map.entry("html5", "html"),
            Map.entry("css3", "css"),
            Map.entry("rest api", "rest"),
            Map.entry("restful", "rest"),
            Map.entry("api rest", "rest"),
            Map.entry("git hub", "github"));

    /** Apelidos que também são palavras comuns: valem no perfil, mas não para achar a competência num texto livre. */
    private static final Set<String> AMBIGUOUS_IN_TEXT = Set.of("net", "node", "ts", "py");

    private SkillNormalizer() {
    }

    public static String normalize(String skill) {
        String plain = TextNormalizer.normalize(skill);
        return ALIASES.getOrDefault(plain, plain);
    }

    /**
     * Todas as grafias (já normalizadas por {@link TextNormalizer}) que levam a esta forma canônica: a própria forma e
     * os apelidos. Ex.: "csharp" → {"csharp", "c#", "c sharp"}. Serve para achar a competência num texto livre, que
     * não passa pelo mapa de apelidos.
     */
    public static Set<String> surfaceForms(String canonical) {
        Set<String> forms = new LinkedHashSet<>();
        forms.add(canonical);
        ALIASES.forEach((alias, target) -> {
            if (target.equals(canonical) && !AMBIGUOUS_IN_TEXT.contains(alias)) {
                forms.add(alias);
            }
        });
        return forms;
    }
}
