package dev.jobdragonquest.common.text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Termos de cargo equivalentes em português, inglês e espanhol, para que uma busca em um idioma encontre vagas escritas
 * em outro ("Estágio" encontra "Internship" e "Pasantía"). Lista explícita e determinística, já sem acento.
 */
public final class MultilingualTerms {

    private static final List<Set<String>> GROUPS = List.of(
            Set.of("desenvolvedor", "desenvolvedora", "developer", "desarrollador", "desarrolladora", "programador", "programadora",
                    "programmer", "dev"),
            Set.of("engenheiro", "engenheira", "engineer", "ingeniero", "ingeniera"),
            Set.of("estagio", "estagiario", "estagiaria", "intern", "internship", "pasante", "pasantia", "practicante", "practicas",
                    "becario", "becaria", "coop"),
            Set.of("junior", "jr"),
            Set.of("pleno", "plena", "mid", "midlevel", "semisenior", "ssr"),
            Set.of("senior", "sr"),
            Set.of("analista", "analyst"),
            Set.of("dados", "data", "datos"),
            Set.of("suporte", "support", "soporte"),
            Set.of("testes", "teste", "tester", "qa", "pruebas"),
            Set.of("seguranca", "security", "seguridad"),
            Set.of("tecnologia", "technology"),
            Set.of("sistemas", "systems"),
            Set.of("remoto", "remota", "remote"));

    private static final Map<String, Set<String>> INDEX = index();

    private MultilingualTerms() {
    }

    private static Map<String, Set<String>> index() {
        Map<String, Set<String>> found = new HashMap<>();
        GROUPS.forEach(group -> group.forEach(word -> found.put(word, group)));
        return Map.copyOf(found);
    }

    /** As duas palavras (já normalizadas) são o mesmo termo em idiomas diferentes. */
    public static boolean equivalent(String a, String b) {
        Set<String> group = INDEX.get(a);
        return group != null && group.contains(b);
    }

    /** A palavra e seus equivalentes; a própria palavra quando não é um termo conhecido. */
    public static Set<String> equivalents(String word) {
        return INDEX.getOrDefault(word, Set.of(word));
    }

    /** Junta as grafias separadas ("back end", "full stack", "co op") para compará-las como uma palavra só. */
    public static String joinCompounds(String normalized) {
        return (" " + normalized + " ")
                .replace(" back end ", " backend ").replace(" front end ", " frontend ").replace(" full stack ", " fullstack ")
                .replace(" co op ", " coop ").replace(" mid level ", " midlevel ").replace(" semi senior ", " semisenior ")
                .trim();
    }
}
