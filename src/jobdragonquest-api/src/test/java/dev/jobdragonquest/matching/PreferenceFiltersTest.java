package dev.jobdragonquest.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.common.domain.Seniority;
import dev.jobdragonquest.common.domain.WorkMode;
import dev.jobdragonquest.jobs.JobSnapshot;
import dev.jobdragonquest.matching.MatchResult.Blocker;
import dev.jobdragonquest.profiles.ProfileEnums.EmploymentType;
import dev.jobdragonquest.profiles.ProfileEnums.SalaryPeriod;
import dev.jobdragonquest.profiles.ProfileSnapshot;
import dev.jobdragonquest.profiles.ProfileSnapshot.Filters;

/** Filtros de preferência do perfil (issue #102). */
class PreferenceFiltersTest {

    private static Filters filters(List<String> companies, List<String> include, List<String> exclude,
            Set<EmploymentType> types, Set<String> countries, String salary, SalaryPeriod period) {
        return new Filters(companies, include, exclude, types, countries, salary == null ? null : new BigDecimal(salary), "BRL", period);
    }

    private static ProfileSnapshot profile(Filters filters) {
        return new ProfileSnapshot(UUID.randomUUID(), "Java", Seniority.JUNIOR, 60, List.of("Desenvolvedora Java"), List.of(),
                Set.of(), Map.of(), Map.of(), filters);
    }

    private static JobSnapshot job(String company, String title, String description, String country, WorkMode mode,
            JobSnapshot.Salary salary) {
        return new JobSnapshot(UUID.randomUUID(), company, title, null, country, mode, null, "https://x.test/1", List.of(),
                List.of(), description, salary);
    }

    private static List<String> codes(ProfileSnapshot profile, JobSnapshot job, List<String> warnings) {
        List<Blocker> blockers = new ArrayList<>();
        PreferenceFilters.check(profile, job, blockers, warnings);
        return blockers.stream().map(Blocker::code).toList();
    }

    @Test
    void excludedCompanyAndKeywordsUseWholeWordsWithoutAccents() {
        Filters f = filters(List.of("Acme"), List.of(), List.of("Kafka", "java"), Set.of(), Set.of(), null, null);
        assertThat(PreferenceFilters.hardBlockers(f, "ACME Ltda", "Dev", "")).extracting(Blocker::code).containsExactly("EXCLUDED_COMPANY");
        // "Acmeísta" e "JavaScript" não são as palavras excluídas.
        assertThat(PreferenceFilters.hardBlockers(f, "Acmeísta", "Dev JavaScript", "React")).isEmpty();
        assertThat(PreferenceFilters.hardBlockers(f, "Outra", "Dev", "Usamos KAFKA")).extracting(Blocker::code).containsExactly("EXCLUDED_KEYWORD");
    }

    @Test
    void includeKeywordsRequireAtLeastOne() {
        Filters f = filters(List.of(), List.of("Spring Boot", "Quarkus"), List.of(), Set.of(), Set.of(), null, null);
        assertThat(PreferenceFilters.hardBlockers(f, "X", "Dev Java", "Trabalho com spring boot")).isEmpty();
        assertThat(PreferenceFilters.hardBlockers(f, "X", "Dev Java", "Trabalho com Spring")).extracting(Blocker::code)
                .containsExactly("MISSING_KEYWORD");
    }

    @Test
    void employmentTypeIsDetectedAndCompared() {
        assertThat(PreferenceFilters.detectEmploymentTypes("Estágio em Desenvolvimento", "")).containsExactly(EmploymentType.INTERNSHIP);
        // "internacional" não é estágio, e estágio citado só na descrição não conta.
        assertThat(PreferenceFilters.detectEmploymentTypes("Dev internacional", "temos programa de estágio")).isEmpty();
        assertThat(PreferenceFilters.detectEmploymentTypes("Dev Java", "Contratação CLT ou PJ"))
                .containsExactlyInAnyOrder(EmploymentType.CLT, EmploymentType.PJ);

        ProfileSnapshot cltOnly = profile(filters(List.of(), List.of(), List.of(), Set.of(EmploymentType.CLT), Set.of(), null, null));
        assertThat(codes(cltOnly, job("X", "Dev Java", "Vaga PJ", null, null, null), new ArrayList<>())).containsExactly("EMPLOYMENT_TYPE");
        assertThat(codes(cltOnly, job("X", "Dev Java", "CLT ou PJ", null, null, null), new ArrayList<>())).isEmpty();
        assertThat(codes(cltOnly, job("X", "Dev Java", "sem regime informado", null, null, null), new ArrayList<>())).isEmpty();
        ProfileSnapshot fullTime = profile(filters(List.of(), List.of(), List.of(), Set.of(EmploymentType.FULL_TIME), Set.of(), null, null));
        assertThat(codes(fullTime, job("X", "Dev Java", "Regime CLT", null, null, null), new ArrayList<>())).isEmpty();
    }

    @Test
    void workAuthorizationBlocksOnsiteAndWarnsRemote() {
        ProfileSnapshot brOnly = profile(filters(List.of(), List.of(), List.of(), Set.of(), Set.of("BR"), null, null));
        List<String> warnings = new ArrayList<>();
        assertThat(codes(brOnly, job("X", "Dev", "", "US", WorkMode.ONSITE, null), warnings)).containsExactly("WORK_AUTHORIZATION");
        assertThat(codes(brOnly, job("X", "Dev", "", "US", WorkMode.REMOTE, null), warnings)).isEmpty();
        assertThat(warnings).anyMatch(w -> w.contains("remota"));
        assertThat(codes(brOnly, job("X", "Dev", "", "br", WorkMode.ONSITE, null), new ArrayList<>())).isEmpty();
        assertThat(codes(brOnly, job("X", "Dev", "", null, WorkMode.ONSITE, null), new ArrayList<>())).isEmpty();
    }

    @Test
    void salaryComparesMonthAndYearButNotHourOrOtherCurrency() {
        ProfileSnapshot wants5k = profile(filters(List.of(), List.of(), List.of(), Set.of(), Set.of(), "5000", SalaryPeriod.MONTHLY));
        assertThat(codes(wants5k, job("X", "Dev", "", null, null, new JobSnapshot.Salary(null, new BigDecimal("4000"), "BRL", "MONTH")),
                new ArrayList<>())).containsExactly("SALARY");
        // 72.000 por ano = 6.000 por mês: passa.
        assertThat(codes(wants5k, job("X", "Dev", "", null, null, new JobSnapshot.Salary(null, new BigDecimal("72000"), "BRL", "YEAR")),
                new ArrayList<>())).isEmpty();
        assertThat(codes(wants5k, job("X", "Dev", "", null, null, new JobSnapshot.Salary(new BigDecimal("3000"), null, "BRL", "MONTH")),
                new ArrayList<>())).containsExactly("SALARY");
        assertThat(codes(wants5k, job("X", "Dev", "", null, null, new JobSnapshot.Salary(null, new BigDecimal("20"), "BRL", "HOUR")),
                new ArrayList<>())).isEmpty();
        List<String> warnings = new ArrayList<>();
        assertThat(codes(wants5k, job("X", "Dev", "", null, null, new JobSnapshot.Salary(null, new BigDecimal("100"), "USD", "MONTH")),
                warnings)).isEmpty();
        assertThat(warnings).anyMatch(w -> w.contains("USD"));
        assertThat(PreferenceFilters.convert(new BigDecimal("1000"), "MONTH", SalaryPeriod.YEARLY)).contains(new BigDecimal("12000"));
        assertThat(PreferenceFilters.convert(new BigDecimal("1000"), null, SalaryPeriod.MONTHLY)).isEmpty();
    }

    @Test
    void noFiltersMeansNoBlockers() {
        assertThat(codes(profile(Filters.NONE), job("Acme", "Estágio", "PJ Kafka", "US", WorkMode.ONSITE,
                new JobSnapshot.Salary(null, BigDecimal.ONE, "BRL", "MONTH")), new ArrayList<>())).isEmpty();
    }
}
