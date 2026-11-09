package dev.jobdragonquest.common.text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import dev.jobdragonquest.common.domain.Seniority;

/** Nível da vaga deduzido do título, em português, inglês ou espanhol. Sem indício claro, devolve null. */
public final class TitleSeniority {

    private static final Map<Seniority, Pattern> RULES = rules();

    private TitleSeniority() {
    }

    private static Map<Seniority, Pattern> rules() {
        Map<Seniority, Pattern> rules = new LinkedHashMap<>();
        rules.put(Seniority.INTERN, word("estagio|estagiari[oa]s?|interns?|internship|pasante|pasantia|practicante|practicas|becari[oa]|co op|coop|student"));
        rules.put(Seniority.TRAINEE, word("trainee"));
        rules.put(Seniority.ENTRY, word("entry level|new grad|new graduate|recem formad[oa]"));
        rules.put(Seniority.JUNIOR, word("junior|jr|associate|assoc|software engineer i|engineer 1|developer i|sde i"));
        rules.put(Seniority.SENIOR, word("senior|sr"));
        rules.put(Seniority.SPECIALIST, word("staff|principal|especialista|specialist"));
        rules.put(Seniority.LEAD, word("lead|lider|tech lead"));
        rules.put(Seniority.MANAGER, word("manager|gerente"));
        rules.put(Seniority.DIRECTOR, word("director|diretor|diretora|head of"));
        rules.put(Seniority.MID, word("pleno|plena|mid level|mid|semi senior|ssr|software engineer ii|engineer 2"));
        return rules;
    }

    private static Pattern word(String alternatives) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])(" + alternatives + ")(?![\\p{L}\\p{N}])");
    }

    public static Seniority infer(String title) {
        String text = TextNormalizer.normalize(title).replaceAll("[./+#-]", " ").replaceAll("\\s+", " ").trim();
        for (Map.Entry<Seniority, Pattern> rule : RULES.entrySet()) {
            if (rule.getValue().matcher(text).find()) {
                return rule.getKey();
            }
        }
        return null;
    }
}
