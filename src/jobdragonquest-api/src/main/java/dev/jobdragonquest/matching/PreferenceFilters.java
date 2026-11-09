package dev.jobdragonquest.matching;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import dev.jobdragonquest.common.domain.WorkMode;
import dev.jobdragonquest.common.text.MultilingualTerms;
import dev.jobdragonquest.common.text.TextLanguage;
import dev.jobdragonquest.common.text.TextNormalizer;
import dev.jobdragonquest.jobs.JobSnapshot;
import dev.jobdragonquest.matching.MatchResult.Blocker;
import dev.jobdragonquest.profiles.ProfileEnums.EmploymentType;
import dev.jobdragonquest.profiles.ProfileEnums.SalaryPeriod;
import dev.jobdragonquest.profiles.ProfileSnapshot;
import dev.jobdragonquest.profiles.ProfileSnapshot.Filters;

/**
 * Filtros de preferência do perfil, aplicados como impedimentos explicados. Função pura, sem acesso a banco.
 *
 * <p>Palavras e empresas são comparadas como palavras inteiras, sem acento e sem diferença de maiúsculas:
 * excluir "Java" não exclui "JavaScript". Quando a vaga não informa o dado (país, salário, tipo de contrato),
 * o filtro não bloqueia: na dúvida a vaga fica para a pessoa decidir.
 */
public final class PreferenceFilters {

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    /** Tipos de contrato reconhecíveis no texto da vaga. Estágio, aprendiz e trainee só contam no título. */
    private static final Map<EmploymentType, Pattern> TITLE_TYPES = Map.of(
            EmploymentType.INTERNSHIP, word("estagi\\w*|intern|interns|internship|pasantia|pasante|practicante|becario|becaria"),
            EmploymentType.APPRENTICE, word("jovem aprendiz|aprendiz|apprentice|apprenticeship"),
            EmploymentType.TRAINEE, word("trainee"));
    private static final Map<EmploymentType, Pattern> TEXT_TYPES = Map.of(
            EmploymentType.CLT, word("clt"),
            EmploymentType.PJ, word("pj|pessoa juridica"),
            EmploymentType.PART_TIME, word("meio periodo|part time|tiempo parcial|medio tiempo"),
            EmploymentType.TEMPORARY, word("temporario|temporaria|temporary|temporal"),
            EmploymentType.FREELANCE, word("freelance|freelancer|freela"),
            EmploymentType.VOLUNTEER, word("voluntario|voluntaria|volunteer"));

    private PreferenceFilters() {
    }

    /**
     * Filtros que tiram a vaga da coleta automática: empresa excluída, palavra excluída ou nenhuma palavra obrigatória.
     * Usa só o que a coleta já tem (empresa, cargo e descrição).
     */
    public static List<Blocker> hardBlockers(Filters filters, String company, String title, String description) {
        List<Blocker> blockers = new ArrayList<>();
        String companyText = TextNormalizer.normalize(company);
        filters.excludedCompanies().stream()
                .filter(excluded -> containsWords(companyText, excluded))
                .findFirst()
                .ifPresent(excluded -> blockers.add(new Blocker("EXCLUDED_COMPANY", "A empresa " + company + " está na lista de empresas excluídas")));
        String text = TextNormalizer.normalize(nullToEmpty(title) + " " + nullToEmpty(description));
        filters.excludeKeywords().stream()
                .filter(keyword -> containsWords(text, keyword))
                .findFirst()
                .ifPresent(keyword -> blockers.add(new Blocker("EXCLUDED_KEYWORD", "A vaga menciona \"" + keyword + "\", uma palavra excluída")));
        String language = filters.jobLanguages().isEmpty() ? null : TextLanguage.guess(nullToEmpty(title) + " " + nullToEmpty(description));
        if (language != null && !filters.jobLanguages().contains(language)) {
            blockers.add(new Blocker("JOB_LANGUAGE", "A vaga está em um idioma fora dos escolhidos no perfil: " + language));
        }
        if (!filters.includeKeywords().isEmpty() && filters.includeKeywords().stream().noneMatch(keyword -> containsWords(text, keyword))) {
            blockers.add(new Blocker("MISSING_KEYWORD", "A vaga não menciona nenhuma das palavras obrigatórias: "
                    + String.join(", ", filters.includeKeywords())));
        }
        return blockers;
    }

    /** Todos os filtros de preferência, com o que a vaga cadastrada informa. */
    static void check(ProfileSnapshot profile, JobSnapshot job, List<Blocker> blockers, List<String> warnings) {
        Filters filters = profile.filters();
        blockers.addAll(hardBlockers(filters, job.company(), job.title(), job.description()));
        checkEmploymentType(filters, job, blockers);
        checkWorkAuthorization(filters, job, blockers, warnings);
        checkSalary(filters, job, blockers, warnings);
    }

    /** Tipos de contrato que o texto da vaga declara. Conjunto vazio quando a vaga não diz. */
    static Set<EmploymentType> detectEmploymentTypes(String title, String description) {
        Set<EmploymentType> found = EnumSet.noneOf(EmploymentType.class);
        String titleText = " " + TextNormalizer.normalize(title) + " ";
        String allText = " " + TextNormalizer.normalize(nullToEmpty(title) + " " + nullToEmpty(description)) + " ";
        TITLE_TYPES.forEach((type, pattern) -> {
            if (pattern.matcher(titleText).find()) {
                found.add(type);
            }
        });
        TEXT_TYPES.forEach((type, pattern) -> {
            if (pattern.matcher(allText).find()) {
                found.add(type);
            }
        });
        return found;
    }

    private static void checkEmploymentType(Filters filters, JobSnapshot job, List<Blocker> blockers) {
        if (filters.employmentTypes().isEmpty()) {
            return;
        }
        Set<EmploymentType> offered = detectEmploymentTypes(job.title(), job.description());
        if (offered.isEmpty()) {
            return;
        }
        Set<EmploymentType> accepted = EnumSet.copyOf(filters.employmentTypes());
        // Tempo integral aceita CLT, que é o regime integral padrão no Brasil.
        if (accepted.contains(EmploymentType.FULL_TIME)) {
            accepted.add(EmploymentType.CLT);
        }
        if (offered.stream().noneMatch(accepted::contains)) {
            blockers.add(new Blocker("EMPLOYMENT_TYPE", "A vaga é " + offered + " e o perfil aceita " + filters.employmentTypes()));
        }
    }

    private static void checkWorkAuthorization(Filters filters, JobSnapshot job, List<Blocker> blockers, List<String> warnings) {
        if (filters.workAuthorization().isEmpty() || job.country() == null) {
            return;
        }
        String country = job.country().toUpperCase(Locale.ROOT);
        if (filters.workAuthorization().contains(country)) {
            return;
        }
        String message = "A vaga é no país " + country + ", fora dos países onde o perfil pode trabalhar " + filters.workAuthorization();
        boolean presential = job.workMode() == WorkMode.ONSITE || job.workMode() == WorkMode.HYBRID;
        if (presential) {
            blockers.add(new Blocker("WORK_AUTHORIZATION", message));
        } else {
            // Vaga remota: o país costuma ser a sede da empresa, que pode contratar de fora. Fica o alerta.
            warnings.add(message + " (remota: confira se contratam de onde você está)");
        }
    }

    private static void checkSalary(Filters filters, JobSnapshot job, List<Blocker> blockers, List<String> warnings) {
        JobSnapshot.Salary salary = job.salary();
        if (filters.salaryExpectation() == null || salary == null) {
            return;
        }
        BigDecimal offered = salary.max() != null ? salary.max() : salary.min();
        if (offered == null) {
            return;
        }
        if (filters.salaryCurrency() != null && salary.currency() != null
                && !filters.salaryCurrency().equalsIgnoreCase(salary.currency())) {
            warnings.add("Salário em " + salary.currency() + ", diferente da moeda do perfil (" + filters.salaryCurrency() + ")");
            return;
        }
        SalaryPeriod wanted = filters.salaryPeriod() == null ? SalaryPeriod.MONTHLY : filters.salaryPeriod();
        Optional<BigDecimal> comparable = convert(offered, salary.period(), wanted);
        if (comparable.isEmpty()) {
            return;
        }
        if (comparable.get().compareTo(filters.salaryExpectation()) < 0) {
            blockers.add(new Blocker("SALARY", "A vaga paga até " + plain(comparable.get()) + " " + label(wanted)
                    + " e o perfil pede " + plain(filters.salaryExpectation())));
        }
    }

    /** Converte só entre mês e ano (divide ou multiplica por 12). Hora só se compara com hora. */
    static Optional<BigDecimal> convert(BigDecimal value, String jobPeriod, SalaryPeriod wanted) {
        if (jobPeriod == null) {
            return Optional.empty();
        }
        return switch (jobPeriod) {
            case "HOUR" -> wanted == SalaryPeriod.HOURLY ? Optional.of(value) : Optional.empty();
            case "MONTH" -> switch (wanted) {
                case MONTHLY -> Optional.of(value);
                case YEARLY -> Optional.of(value.multiply(TWELVE));
                case HOURLY -> Optional.empty();
            };
            case "YEAR" -> switch (wanted) {
                case YEARLY -> Optional.of(value);
                case MONTHLY -> Optional.of(value.divide(TWELVE, 2, RoundingMode.HALF_UP));
                case HOURLY -> Optional.empty();
            };
            default -> Optional.empty();
        };
    }

    private static String label(SalaryPeriod period) {
        return switch (period) {
            case HOURLY -> "por hora";
            case MONTHLY -> "por mês";
            case YEARLY -> "por ano";
        };
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** O texto (já normalizado) contém a expressão como palavras inteiras. */
    static boolean containsWords(String normalizedText, String expression) {
        String needle = TextNormalizer.normalize(expression);
        if (needle.isEmpty()) {
            return false;
        }
        if (MultilingualTerms.equivalents(needle).size() > 1) {
            return MultilingualTerms.equivalents(needle).stream().anyMatch(term -> literalWords(normalizedText, term));
        }
        return literalWords(normalizedText, needle);
    }

    private static boolean literalWords(String normalizedText, String needle) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(needle) + "(?![\\p{L}\\p{N}])").matcher(normalizedText).find();
    }

    private static Pattern word(String alternatives) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
