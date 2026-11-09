package dev.jobdragonquest.career;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.jobdragonquest.common.text.SkillNormalizer;
import dev.jobdragonquest.common.text.TitleMatcher;
import dev.jobdragonquest.jobs.JobDtos.JobResponse;
import dev.jobdragonquest.jobs.JobDtos.RequirementDto;
import dev.jobdragonquest.jobs.JobRequirement;
import dev.jobdragonquest.jobs.JobService;
import dev.jobdragonquest.jobs.JobSnapshot;
import dev.jobdragonquest.profiles.ProfileDtos.EntryDto;
import dev.jobdragonquest.profiles.ProfileDtos.ProfileResponse;
import dev.jobdragonquest.profiles.ProfileDtos.SkillView;
import dev.jobdragonquest.profiles.ProfileOptions.Translation;
import dev.jobdragonquest.profiles.ProfileService;

/**
 * Ferramentas de carreira sem custo e sem IA externa (issue #118), sempre a partir dos dados da própria conta:
 * currículo e carta do perfil (geral ou ajustado à vaga, só reordenando e destacando, nunca inventando), preparação de
 * entrevista pelos requisitos da vaga, faixa salarial e competências mais pedidas nas vagas coletadas.
 */
@Service
public class CareerService {

    static final int MARKET_POOL = 300;

    public record ResumeSkill(String name, int level, String time, boolean highlighted) {
    }

    public record ResumeEntry(String kind, String title, String organization, LocalDate startDate, LocalDate endDate, boolean current,
            String location, String description, List<String> skills, boolean highlighted) {
    }

    /** Currículo montado do perfil. {@code jobTitle}/{@code company}: vaga de referência, quando há. */
    public record Resume(String headline, String currentTitle, String location, String summary, List<String> links,
            List<ResumeSkill> skills, List<ResumeEntry> experience, List<ResumeEntry> education, List<ResumeEntry> other,
            List<String> languages, String jobTitle, String company, String language, boolean needsTranslation) {
    }

    public record CoverLetter(String text, List<String> missing, String language, boolean needsTranslation) {
    }

    public record InterviewQuestion(String category, String question, String hint) {
    }

    public record SalaryStats(int jobs, String currency, String period, BigDecimal min, BigDecimal median, BigDecimal max) {
    }

    public record SkillDemand(String skill, int jobs, int percent, boolean inProfile) {
    }

    private final ProfileService profiles;
    private final JobService jobs;
    private final TranslationService translator;

    CareerService(ProfileService profiles, JobService jobs, TranslationService translator) {
        this.profiles = profiles;
        this.jobs = jobs;
        this.translator = translator;
    }

    public Resume resume(UUID userId, UUID profileId, UUID jobId) {
        ProfileResponse p = profiles.get(userId, profileId);
        JobResponse job = jobId == null ? null : jobs.get(userId, jobId);
        Set<String> wanted = job == null ? Set.of() : job.requirements().stream().map(RequirementDto::skill)
                .filter(java.util.Objects::nonNull).map(SkillNormalizer::normalize).collect(Collectors.toSet());

        List<ResumeSkill> skills = p.skills().stream()
                .map(s -> new ResumeSkill(s.name(), s.level(), time(s), wanted.contains(SkillNormalizer.normalize(s.name()))))
                .sorted(Comparator.comparing((ResumeSkill s) -> !s.highlighted()).thenComparing(ResumeSkill::level, Comparator.reverseOrder())
                        .thenComparing(ResumeSkill::name))
                .toList();
        List<ResumeEntry> experience = new ArrayList<>();
        List<ResumeEntry> education = new ArrayList<>();
        List<ResumeEntry> other = new ArrayList<>();
        String language = documentLanguage(p, job);
        Translation translated = translation(p, language);
        boolean[] untranslated = {false};
        java.util.function.UnaryOperator<String> auto = text -> automatic(p, language, text, untranslated);
        for (EntryDto e : p.entries()) {
            boolean relevant = !wanted.isEmpty() && mentions(e, wanted);
            ResumeEntry entry = new ResumeEntry(e.kind().name(), auto.apply(e.title()), e.organization(), e.startDate(), e.endDate(),
                    Boolean.TRUE.equals(e.current()), e.location(), auto.apply(e.description()), entrySkills(e), relevant);
            switch (e.kind()) {
                case EXPERIENCE, ACADEMIC_EXPERIENCE -> experience.add(entry);
                case EDUCATION -> education.add(entry);
                default -> other.add(entry);
            }
        }
        Comparator<ResumeEntry> order = Comparator.comparing((ResumeEntry e) -> !e.highlighted())
                .thenComparing((ResumeEntry e) -> !e.current())
                .thenComparing(e -> e.startDate() == null ? LocalDate.MIN : e.startDate(), Comparator.reverseOrder());
        experience.sort(order);
        education.sort(order);
        other.sort(order);
        List<String> links = new ArrayList<>();
        for (String link : new String[] {p.linkedinUrl(), p.githubUrl(), p.portfolioUrl(), p.websiteUrl()}) {
            if (link != null) {
                links.add(link);
            }
        }
        if (p.options() != null && p.options().extraLinks() != null) {
            p.options().extraLinks().forEach(extra -> links.add(extra.url()));
        }
        List<String> languages = p.languages().stream().map(l -> l.code() + " (" + l.level() + ")").toList();
        String headline = translated != null && translated.headline() != null ? translated.headline() : auto.apply(p.headline());
        String summary = translated != null && translated.summary() != null ? translated.summary() : auto.apply(p.summary());
        return new Resume(headline, p.currentTitle(), join(", ", p.city(), p.stateRegion(), p.country()), summary, links,
                skills, experience, education, other, languages, job == null ? null : job.title(), job == null ? null : job.company(),
                language, untranslated[0]);
    }

    /** Idioma dos documentos: o da vaga, se a opção estiver ligada e der para reconhecer; senão, português. */
    private static String documentLanguage(ProfileResponse p, JobResponse job) {
        if (job == null || p.options() == null || !Boolean.TRUE.equals(p.options().documentsInJobLanguage())) {
            return "pt";
        }
        String guessed = TextLanguage.guess(job.description());
        return guessed == null ? "pt" : guessed;
    }

    /** Texto no idioma do documento: traduzido se a opção estiver ligada; senão o original, marcado como não traduzido. */
    private String automatic(ProfileResponse p, String language, String text, boolean[] untranslated) {
        if (text == null || text.isBlank() || "pt".equals(language)) {
            return text;
        }
        String result = Boolean.TRUE.equals(p.options().autoTranslate()) ? translator.translate(text, language) : null;
        if (result == null) {
            untranslated[0] = true;
            return text;
        }
        return result;
    }

    private static Translation translation(ProfileResponse p, String language) {
        if (p.options() == null || p.options().translations() == null) {
            return null;
        }
        return p.options().translations().stream().filter(t -> t.language().equals(language)).findFirst().orElse(null);
    }

    private static String pick(String language, String pt, String en, String es) {
        return "en".equals(language) ? en : "es".equals(language) ? es : pt;
    }

    /**
     * Carta montada com o que o perfil tem. Se o perfil já tem carta, ela é usada e os marcadores {empresa} e {cargo}
     * são trocados. Sem dado, a frase não entra e o que faltou vai em {@code missing}.
     */
    public CoverLetter coverLetter(UUID userId, UUID profileId, UUID jobId) {
        ProfileResponse p = profiles.get(userId, profileId);
        JobResponse job = jobId == null ? null : jobs.get(userId, jobId);
        String company = job == null ? null : job.company();
        String title = job == null ? null : job.title();
        List<String> missing = new ArrayList<>();
        String language = documentLanguage(p, job);
        Translation translated = translation(p, language);
        boolean english = "en".equals(language);
        boolean[] untranslated = {false};
        java.util.function.UnaryOperator<String> auto = value -> automatic(p, language, value, untranslated);
        String own = translated != null && translated.coverLetter() != null ? translated.coverLetter() : auto.apply(p.coverLetter());
        if (own != null) {
            String text = own.replace("{empresa}", company == null ? pick(language, "a empresa", "the company", "la empresa") : company)
                    .replace("{cargo}", title == null ? pick(language, "a vaga", "the position", "el puesto") : title);
            return new CoverLetter(text, List.of(), language, untranslated[0]);
        }
        String headline = translated != null && translated.headline() != null ? translated.headline() : auto.apply(p.headline());
        String summary = translated != null && translated.summary() != null ? translated.summary() : auto.apply(p.summary());
        StringBuilder text = new StringBuilder();
        text.append(company == null ? pick(language, "Olá,", "Hello,", "Hola,")
                : pick(language, "Olá, equipe da ", "Hello ", "Hola, equipo de ") + company + (english ? " team," : ",")).append("\n\n");
        String role = title == null ? pick(language, "esta oportunidade", "this opportunity", "esta oportunidad")
                : pick(language, "a vaga de ", "the ", "el puesto de ") + title + (english ? " position" : "");
        String who = headline != null ? headline : auto.apply(p.currentTitle());
        text.append(pick(language, "Quero me candidatar a ", "I would like to apply for ", "Quiero postularme a ")).append(role).append(".");
        if (who != null) {
            text.append(pick(language, " Sou ", " I am ", " Soy ")).append(who).append(".");
        } else {
            missing.add(pick(language, "título profissional", "professional title", "título profesional"));
        }
        if (summary != null) {
            text.append(" ").append(summary.strip());
        } else {
            missing.add(pick(language, "resumo profissional", "professional summary", "resumen profesional"));
        }
        text.append("\n\n");
        List<SkillView> matching = matchingSkills(p, job);
        if (!matching.isEmpty()) {
            text.append(job == null ? pick(language, "Minhas principais competências: ", "My main skills: ", "Mis principales competencias: ")
                    : pick(language, "Para esta vaga, destaco: ", "For this position, I highlight: ", "Para este puesto, destaco: "))
                    .append(matching.stream().map(s -> s.name() + ("pt".equals(language) && time(s) != null ? " (" + time(s) + ")" : "")).collect(Collectors.joining(", ")))
                    .append(".\n\n");
        } else {
            missing.add(pick(language, "competências", "skills", "competencias"));
        }
        if (p.availableFrom() != null) {
            text.append(pick(language, "Posso começar a partir de ", "I can start from ", "Puedo empezar a partir de ")).append(p.availableFrom()).append(".\n\n");
        } else if (p.noticePeriodDays() != null) {
            text.append(p.noticePeriodDays() == 0 ? pick(language, "Tenho disponibilidade imediata.", "I am available immediately.", "Tengo disponibilidad inmediata.")
                    : pick(language, "Meu aviso prévio é de ", "My notice period is ", "Mi preaviso es de ") + p.noticePeriodDays() + pick(language, " dias.", " days.", " días.")).append("\n\n");
        }
        text.append(pick(language, "Agradeço a atenção e fico à disposição para conversar.", "Thank you for your time. I would be glad to talk.", "Gracias por su atención. Quedo a disposición para conversar."));
        return new CoverLetter(text.toString(), List.copyOf(missing), language, untranslated[0]);
    }

    public List<InterviewQuestion> interview(UUID userId, UUID jobId, UUID profileId) {
        JobResponse job = jobs.get(userId, jobId);
        ProfileResponse p = profiles.get(userId, profileId);
        Map<String, SkillView> skills = p.skills().stream()
                .collect(Collectors.toMap(s -> SkillNormalizer.normalize(s.name()), s -> s, (a, b) -> a));
        List<InterviewQuestion> list = new ArrayList<>();
        list.add(new InterviewQuestion("opening", "Fale um pouco sobre você.",
                hint(p.headline(), p.summary(), "Resuma em 1 minuto: quem você é, o que faz e por que esta vaga.")));
        list.add(new InterviewQuestion("motivation", "Por que você quer trabalhar na " + job.company() + "?",
                "Pesquise o produto e a missão da empresa e ligue com o que você gosta de fazer."));
        list.add(new InterviewQuestion("behavioral", "Conte uma situação difícil no trabalho ou nos estudos e como resolveu.",
                "Use o método STAR: situação, tarefa, ação e resultado, com números quando der."));
        list.add(new InterviewQuestion("behavioral", "Conte um erro que cometeu e o que aprendeu.",
                "Escolha um erro real, assuma a responsabilidade e mostre o que mudou depois."));
        for (RequirementDto requirement : job.requirements()) {
            if (requirement.skill() == null) {
                continue;
            }
            String shown = requirement.text() != null && requirement.text().length() <= 60 ? requirement.text() : requirement.skill();
            SkillView owned = skills.get(SkillNormalizer.normalize(requirement.skill()));
            if (owned == null) {
                list.add(new InterviewQuestion("gap", "Você tem experiência com " + shown + "?",
                        "O perfil não declara " + shown + ". Seja honesto(a): diga o que sabe de parecido e como aprenderia rápido."));
                continue;
            }
            List<String> where = p.entries().stream()
                    .filter(e -> entrySkills(e).stream().anyMatch(s -> SkillNormalizer.normalize(s).equals(SkillNormalizer.normalize(owned.name())))
                            || e.description() != null && TitleMatcher.containsAllWords(e.description(), owned.name()))
                    .map(e -> e.title() + (e.organization() == null ? "" : " (" + e.organization() + ")")).limit(3).toList();
            String hint = where.isEmpty() ? "Prepare um exemplo concreto do seu uso de " + owned.name() + "."
                    : "Use o exemplo de: " + String.join("; ", where) + ".";
            list.add(new InterviewQuestion(requirement.kind() == JobRequirement.Kind.MANDATORY ? "technical" : "technical_desired",
                    "Conte um projeto em que você usou " + shown + ".", hint + (time(owned) == null ? "" : " Seu tempo: " + time(owned) + ".")));
        }
        list.add(new InterviewQuestion("ask", "O que você quer perguntar à empresa?",
                "Ex.: como é um dia típico, como medem o sucesso no cargo, próximos passos do processo."));
        if (p.salaryExpectation() != null) {
            list.add(new InterviewQuestion("salary", "Qual a sua pretensão salarial?",
                    "Sua pretensão no perfil: " + p.salaryExpectation().stripTrailingZeros().toPlainString()
                            + (p.salaryCurrency() == null ? "" : " " + p.salaryCurrency()) + ". Veja a faixa das vagas coletadas em Carreira."));
        }
        return List.copyOf(list);
    }

    /** Faixa salarial das vagas coletadas da conta com cargo parecido, por moeda e período. */
    public List<SalaryStats> salary(UUID userId, String title) {
        Map<String, List<BigDecimal>> groups = new LinkedHashMap<>();
        for (JobSnapshot job : jobs.recentSnapshots(userId, MARKET_POOL)) {
            JobSnapshot.Salary s = job.salary();
            if (s == null || s.currency() == null || s.period() == null || (title != null && !title.isBlank() && !TitleMatcher.containsAllWords(job.title(), title))) {
                continue;
            }
            BigDecimal value = s.min() != null && s.max() != null
                    ? s.min().add(s.max()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
                    : s.max() != null ? s.max() : s.min();
            if (value != null) {
                groups.computeIfAbsent(s.currency().toUpperCase(Locale.ROOT) + "|" + s.period(), k -> new ArrayList<>()).add(value);
            }
        }
        List<SalaryStats> stats = new ArrayList<>();
        groups.forEach((key, values) -> {
            values.sort(null);
            String[] parts = key.split("\\|");
            int n = values.size();
            BigDecimal median = n % 2 == 1 ? values.get(n / 2)
                    : values.get(n / 2 - 1).add(values.get(n / 2)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            stats.add(new SalaryStats(n, parts[0], parts[1], values.getFirst(), median, values.getLast()));
        });
        stats.sort(Comparator.comparingInt(SalaryStats::jobs).reversed());
        return List.copyOf(stats);
    }

    /** Competências mais pedidas nas vagas da conta (com cargo parecido, se informado), marcando as que o perfil não tem. */
    public List<SkillDemand> skills(UUID userId, UUID profileId, String title) {
        Set<String> owned = profileId == null ? Set.of() : profiles.get(userId, profileId).skills().stream()
                .map(s -> SkillNormalizer.normalize(s.name())).collect(Collectors.toSet());
        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        for (JobSnapshot job : jobs.recentSnapshots(userId, MARKET_POOL)) {
            if (title != null && !title.isBlank() && !TitleMatcher.containsAllWords(job.title(), title)) {
                continue;
            }
            total++;
            job.requirements().stream().map(JobSnapshot.Requirement::skill).filter(java.util.Objects::nonNull)
                    .map(SkillNormalizer::normalize).distinct().forEach(skill -> counts.merge(skill, 1, Integer::sum));
        }
        int jobsSeen = total;
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(30)
                .map(e -> new SkillDemand(e.getKey(), e.getValue(), jobsSeen == 0 ? 0 : Math.round(100f * e.getValue() / jobsSeen), owned.contains(e.getKey())))
                .toList();
    }

    private static List<SkillView> matchingSkills(ProfileResponse p, JobResponse job) {
        if (job == null) {
            return p.skills().stream().sorted(Comparator.comparingInt(SkillView::level).reversed()).limit(5).toList();
        }
        Set<String> wanted = job.requirements().stream().map(RequirementDto::skill).filter(java.util.Objects::nonNull)
                .map(SkillNormalizer::normalize).collect(Collectors.toSet());
        return p.skills().stream().filter(s -> wanted.contains(SkillNormalizer.normalize(s.name())))
                .sorted(Comparator.comparingInt(SkillView::level).reversed()).limit(6).toList();
    }

    private static boolean mentions(EntryDto e, Set<String> wanted) {
        if (entrySkills(e).stream().map(SkillNormalizer::normalize).anyMatch(wanted::contains)) {
            return true;
        }
        String text = (e.title() == null ? "" : e.title()) + " " + (e.description() == null ? "" : e.description());
        return wanted.stream().anyMatch(skill -> TitleMatcher.containsAllWords(text, skill));
    }

    /** Competências do item, que o perfil guarda como texto separado por vírgula ou ponto e vírgula. */
    static List<String> entrySkills(EntryDto e) {
        if (e.skills() == null || e.skills().isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(e.skills().split("[,;]")).map(String::strip).filter(x -> !x.isEmpty()).toList();
    }

    static String time(SkillView s) {
        List<String> parts = new ArrayList<>();
        if (s.years() != null && s.years() > 0) {
            parts.add(s.years() + (s.years() == 1 ? " ano" : " anos"));
        }
        if (s.months() != null && s.months() > 0) {
            parts.add(s.months() + (s.months() == 1 ? " mês" : " meses"));
        }
        return parts.isEmpty() ? null : String.join(" e ", parts);
    }

    private static String hint(String headline, String summary, String fallback) {
        String base = headline != null ? "Comece por: " + headline + "." : null;
        if (summary != null) {
            return (base == null ? "" : base + " ") + "Seu resumo no perfil: " + summary.strip();
        }
        return base == null ? fallback : base + " " + fallback;
    }

    private static String join(String separator, String... values) {
        List<String> present = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                present.add(value.strip());
            }
        }
        return present.isEmpty() ? null : String.join(separator, present);
    }
}
