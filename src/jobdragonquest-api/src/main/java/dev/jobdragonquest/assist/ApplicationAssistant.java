package dev.jobdragonquest.assist;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.jobdragonquest.applications.ApplicationService;
import dev.jobdragonquest.common.text.SkillNormalizer;
import dev.jobdragonquest.jobs.JobDtos.JobResponse;
import dev.jobdragonquest.jobs.JobDtos.RequirementDto;
import dev.jobdragonquest.jobs.JobRequirement;
import dev.jobdragonquest.jobs.JobService;
import dev.jobdragonquest.jobs.JobStatus;
import dev.jobdragonquest.matching.MatchResult;
import dev.jobdragonquest.matching.MatchingEngine;
import dev.jobdragonquest.profiles.ProfileDtos.EntryDto;
import dev.jobdragonquest.profiles.ProfileDtos.ProfileResponse;
import dev.jobdragonquest.profiles.ProfileDtos.SkillView;
import dev.jobdragonquest.profiles.ProfileEntry;
import dev.jobdragonquest.profiles.ProfileService;

/**
 * Ajuda na hora de se candidatar (issue #115), sempre com os dados reais do perfil: o que não está no perfil volta
 * como faltando, para a pessoa completar, e nunca é inventado.
 *
 * <ul>
 *   <li>{@link #answers}: respostas sugeridas para as perguntas comuns de formulário (pretensão, aviso prévio, país,
 *       visto, mudança, links, tempo com cada tecnologia pedida, idiomas, formação, carta) e as respostas prontas.</li>
 *   <li>{@link #review}: checagem antes de enviar (vaga aberta, impedimentos, obrigatórios, candidatura repetida,
 *       dados que formulários pedem, carta e links).</li>
 * </ul>
 */
@Service
public class ApplicationAssistant {

    /** Resposta sugerida. {@code answer} nulo quando falta o dado no perfil ({@code missing} diz onde completar). */
    public record SuggestedAnswer(String category, String question, String answer, String source, String missing) {
    }

    public enum CheckStatus {
        OK,
        WARN,
        BLOCK
    }

    public record Check(String code, CheckStatus status, String message) {
    }

    /** {@code ready}: nenhuma checagem bloqueia. */
    public record Review(boolean ready, List<Check> checks) {
    }

    private final JobService jobs;
    private final ProfileService profiles;
    private final MatchingEngine matching;
    private final ApplicationService applications;

    ApplicationAssistant(JobService jobs, ProfileService profiles, MatchingEngine matching, ApplicationService applications) {
        this.jobs = jobs;
        this.profiles = profiles;
        this.matching = matching;
        this.applications = applications;
    }

    public List<SuggestedAnswer> answers(UUID userId, UUID jobId, UUID profileId) {
        JobResponse job = jobs.get(userId, jobId);
        ProfileResponse p = profiles.get(userId, profileId);
        List<SuggestedAnswer> list = new ArrayList<>();

        String salary = p.salaryExpectation() == null ? null
                : plain(p.salaryExpectation()) + " " + (p.salaryCurrency() == null ? "" : p.salaryCurrency())
                        + (p.salaryPeriod() == null ? "" : " (" + p.salaryPeriod().name().toLowerCase(Locale.ROOT) + ")");
        list.add(answer("salary", "Qual a sua pretensão salarial?", salary == null ? null : salary.strip(), "salaryExpectation"));
        if (p.currentSalary() != null) {
            list.add(answer("salary", "Qual o seu salário atual?", plain(p.currentSalary()), "currentSalary"));
        }
        String start = p.availableFrom() != null ? "A partir de " + p.availableFrom()
                : p.noticePeriodDays() != null ? (p.noticePeriodDays() == 0 ? "Disponível de imediato" : "Aviso prévio de " + p.noticePeriodDays() + " dias")
                : p.availability();
        list.add(answer("start", "Quando você pode começar?", start, "availableFrom/noticePeriodDays"));
        String location = join(", ", p.city(), p.stateRegion(), p.country());
        list.add(answer("location", "Onde você mora?", location, "city/stateRegion/country"));
        String authorization = p.workAuthorization() == null || p.workAuthorization().isEmpty() ? null
                : "Posso trabalhar em: " + String.join(", ", p.workAuthorization());
        list.add(answer("work_authorization", "Você tem autorização para trabalhar no país da vaga?", authorization, "workAuthorization"));
        if (p.requiresSponsorship() != null) {
            list.add(answer("sponsorship", "Você precisa de patrocínio de visto?", p.requiresSponsorship() ? "Sim" : "Não", "requiresSponsorship"));
        } else {
            list.add(answer("sponsorship", "Você precisa de patrocínio de visto?", null, "requiresSponsorship"));
        }
        list.add(answer("relocation", "Você aceita se mudar de cidade?",
                p.willingToRelocate() == null ? null : p.willingToRelocate() ? "Sim" : "Não", "willingToRelocate"));
        list.add(answer("linkedin", "Link do seu LinkedIn", p.linkedinUrl(), "linkedinUrl"));
        if (p.githubUrl() != null || mentionsCode(job)) {
            list.add(answer("github", "Link do seu GitHub", p.githubUrl(), "githubUrl"));
        }
        if (p.portfolioUrl() != null) {
            list.add(answer("portfolio", "Link do seu portfólio", p.portfolioUrl(), "portfolioUrl"));
        }

        // Tempo com cada tecnologia que a vaga pede.
        Map<String, SkillView> skills = p.skills().stream()
                .collect(Collectors.toMap(s -> SkillNormalizer.normalize(s.name()), Function.identity(), (a, b) -> a));
        for (RequirementDto requirement : job.requirements()) {
            if (requirement.skill() == null) {
                continue;
            }
            String skill = SkillNormalizer.normalize(requirement.skill());
            SkillView owned = skills.get(skill);
            // Na pergunta, o nome como a vaga escreveu (o campo skill vem normalizado, em minúsculas).
            String shown = requirement.text() != null && requirement.text().length() <= 60 ? requirement.text() : requirement.skill();
            String question = "Quanto tempo de experiência você tem com " + shown + "?";
            if (owned == null) {
                list.add(new SuggestedAnswer("experience", question, null, "skills",
                        "O perfil não declara " + shown + ". Só adicione se você realmente tiver essa experiência."));
            } else if (owned.years() == null && owned.months() == null && owned.days() == null) {
                list.add(new SuggestedAnswer("experience", question, null, "skills", "Informe no perfil o tempo de uso de " + owned.name() + "."));
            } else {
                list.add(answer("experience", question, duration(owned), "skills"));
            }
            if (requirement.kind() == JobRequirement.Kind.MANDATORY && requirement.minYears() != null && owned != null
                    && owned.totalYears() != null && owned.totalYears().compareTo(requirement.minYears()) < 0) {
                list.add(new SuggestedAnswer("experience_gap", "A vaga pede " + plain(requirement.minYears()) + " ano(s) de " + shown,
                        duration(owned), "skills", "Seu tempo é menor que o pedido: responda com o tempo real."));
            }
        }
        String languages = p.languages().isEmpty() ? null
                : p.languages().stream().map(l -> l.code() + " " + l.level()).collect(Collectors.joining(", "));
        list.add(answer("languages", "Quais idiomas você fala e em que nível?", languages, "languages"));
        Optional<EntryDto> education = p.entries().stream().filter(e -> e.kind() == ProfileEntry.Kind.EDUCATION).findFirst();
        list.add(answer("education", "Qual a sua formação?",
                education.map(e -> join(" — ", e.title(), e.organization(), e.status() == null ? null : e.status().name())).orElse(null),
                "entries (formação)"));
        list.add(answer("cover_letter", "Carta de apresentação", p.coverLetter(), "coverLetter"));
        p.answers().forEach(a -> list.add(new SuggestedAnswer("saved", a.question(), a.answer(), "answers", null)));
        return List.copyOf(list);
    }

    public Review review(UUID userId, UUID jobId, UUID profileId) {
        JobResponse job = jobs.get(userId, jobId);
        ProfileResponse p = profiles.get(userId, profileId);
        MatchResult match = matching.evaluate(profiles.snapshot(userId, profileId), jobs.snapshot(userId, jobId));
        List<Check> checks = new ArrayList<>();

        checks.add(job.status() == JobStatus.CLOSED ? new Check("job_closed", CheckStatus.BLOCK, "A vaga está encerrada.")
                : new Check("job_open", CheckStatus.OK, "A vaga não está marcada como encerrada."));
        if (match.blockers().isEmpty()) {
            checks.add(new Check("no_blockers", CheckStatus.OK, "Nenhum impedimento eliminatório."));
        } else {
            match.blockers().forEach(b -> checks.add(new Check("blocker_" + b.code().toLowerCase(Locale.ROOT), CheckStatus.BLOCK, b.message())));
        }
        long unmet = match.requirements().stream()
                .filter(r -> r.kind() == JobRequirement.Kind.MANDATORY && r.outcome() == MatchResult.Outcome.UNMET).count();
        checks.add(unmet == 0 ? new Check("mandatory_met", CheckStatus.OK, "Nenhum requisito obrigatório sem atender.")
                : new Check("mandatory_unmet", CheckStatus.WARN, unmet + " requisito(s) obrigatório(s) não atendido(s): leia com atenção antes de enviar."));
        if (match.score() != null) {
            checks.add(new Check("score", match.score() >= p.minMatchScore() ? CheckStatus.OK : CheckStatus.WARN,
                    "Compatibilidade " + match.score() + " (mínimo do perfil " + p.minMatchScore() + ")."));
        }
        applications.existingFor(userId, jobId, job.company(), job.title()).ifPresentOrElse(
                id -> checks.add(new Check("already_applied", CheckStatus.WARN, "Já existe uma candidatura para esta vaga ou para o mesmo cargo na empresa.")),
                () -> checks.add(new Check("not_applied", CheckStatus.OK, "Nenhuma candidatura repetida.")));
        List<String> missing = new ArrayList<>();
        if (p.linkedinUrl() == null) {
            missing.add("LinkedIn");
        }
        if (p.city() == null && p.country() == null) {
            missing.add("cidade ou país");
        }
        if (p.salaryExpectation() == null) {
            missing.add("pretensão salarial");
        }
        if (p.availableFrom() == null && p.noticePeriodDays() == null && p.availability() == null) {
            missing.add("disponibilidade");
        }
        if (p.workAuthorization() == null || p.workAuthorization().isEmpty()) {
            missing.add("países onde pode trabalhar");
        }
        checks.add(missing.isEmpty() ? new Check("form_data", CheckStatus.OK, "O perfil tem os dados que formulários costumam pedir.")
                : new Check("form_data_missing", CheckStatus.WARN, "Complete no perfil: " + String.join(", ", missing) + "."));
        checks.add(p.coverLetter() == null ? new Check("cover_letter_missing", CheckStatus.WARN, "O perfil não tem carta de apresentação.")
                : new Check("cover_letter", CheckStatus.OK, "Há carta de apresentação no perfil: ajuste para esta vaga."));
        boolean hasExperience = p.entries().stream().anyMatch(e -> e.kind() == ProfileEntry.Kind.EXPERIENCE);
        checks.add(hasExperience || p.skills().size() > 0 ? new Check("profile_content", CheckStatus.OK, "O perfil tem experiências ou competências.")
                : new Check("profile_empty", CheckStatus.WARN, "O perfil não tem experiências nem competências."));
        boolean ready = checks.stream().noneMatch(c -> c.status() == CheckStatus.BLOCK);
        return new Review(ready, List.copyOf(checks));
    }

    private static SuggestedAnswer answer(String category, String question, String value, String source) {
        return value == null || value.isBlank() ? new SuggestedAnswer(category, question, null, source, "Complete no perfil: " + source + ".")
                : new SuggestedAnswer(category, question, value, source, null);
    }

    private static boolean mentionsCode(JobResponse job) {
        return job.requirements().stream().anyMatch(r -> r.skill() != null);
    }

    static String duration(SkillView skill) {
        List<String> parts = new ArrayList<>();
        if (skill.years() != null && skill.years() > 0) {
            parts.add(skill.years() + (skill.years() == 1 ? " ano" : " anos"));
        }
        if (skill.months() != null && skill.months() > 0) {
            parts.add(skill.months() + (skill.months() == 1 ? " mês" : " meses"));
        }
        if (skill.days() != null && skill.days() > 0) {
            parts.add(skill.days() + (skill.days() == 1 ? " dia" : " dias"));
        }
        return parts.isEmpty() ? "Menos de um dia" : String.join(", ", parts);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
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
