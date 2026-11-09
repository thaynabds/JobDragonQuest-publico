package dev.jobdragonquest.career;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;

/** Currículo, carta, entrevista, salário e competências (issue #118): só dados da conta, sem inventar. */
class CareerTest extends ApiTestSupport {

    private static final String PROFILE = """
            {"name":"Java","seniority":"JUNIOR","headline":"Desenvolvedora back-end Java","summary":"Gosto de APIs bem testadas.",
             "noticePeriodDays":15,"salaryExpectation":5000,"salaryCurrency":"BRL","linkedinUrl":"https://linkedin.com/in/teste",
             "skills":[{"name":"Java","level":4,"years":2},{"name":"Python","level":2},{"name":"Spring Boot","level":3,"months":8}],
             "entries":[{"kind":"EXPERIENCE","title":"Estágio em desenvolvimento","organization":"Empresa A","startDate":"2025-01-01",
                         "description":"APIs em Spring Boot","skills":"Spring Boot"},
                        {"kind":"PROJECT","title":"Bot de jogos","description":"Python"},
                        {"kind":"EDUCATION","title":"Análise e Desenvolvimento de Sistemas","organization":"Faculdade"}]}""";

    private String job(String token, String n, String title, String salary, String... skills) throws Exception {
        StringBuilder reqs = new StringBuilder();
        for (String skill : skills) {
            reqs.append(reqs.length() == 0 ? "" : ",").append("{\"kind\":\"MANDATORY\",\"text\":\"%s\",\"skill\":\"%s\"}".formatted(skill, skill));
        }
        return read(postJson(token, "/api/v1/jobs", """
                {"source":"MANUAL","url":"https://carreira.test/%s","company":"Empresa %s","title":"%s",%s"requirements":[%s]}"""
                .formatted(n, n, title, salary, reqs)).andExpect(status().isCreated()), "$.id");
    }

    @Test
    void itemEscondidoSaiDoCurriculoEDadoSensivelSoComPermissao() throws Exception {
        String token = newUser();
        String body = """
                {"name":"Escolhas","seniority":"JUNIOR","headline":"Some","summary":"Fica",
                 "skills":[{"name":"Java","level":4},{"name":"Cobol","level":1,"showInResume":false}],
                 "languages":[{"code":"en","level":"B2","showInResume":false}],
                 "entries":[{"kind":"PROJECT","title":"Visivel","extraUrls":["https://exemplo.test/a","https://exemplo.test/b"]},
                            {"kind":"PROJECT","title":"Oculto","showInResume":false}],
                 "options":{"resumeHidden":["HEADLINE"],"birthDate":"2000-01-02","gpa":"3.5","resumeVisible":["GPA"],
                            "diversityOther":["Texto livre"],
                            "extraLinks":[{"label":"A","url":"https://exemplo.test/um"},
                                          {"label":"B","url":"https://exemplo.test/dois","showInResume":false}]}}""";
        String profile = read(postJson(token, "/api/v1/profiles", body).andExpect(status().isCreated()), "$.id");
        getJson(token, "/api/v1/profiles/" + profile).andExpect(jsonPath("$.entries[0].extraUrls.length()").value(2));
        getJson(token, "/api/v1/profiles/" + profile + "/resume")
                .andExpect(jsonPath("$.headline").doesNotExist())
                .andExpect(jsonPath("$.summary").value("Fica"))
                .andExpect(jsonPath("$.skills[*].name").value(not(hasItem("Cobol"))))
                .andExpect(jsonPath("$.languages.length()").value(0))
                .andExpect(jsonPath("$.other[*].title").value(not(hasItem("Oculto"))))
                .andExpect(jsonPath("$.links").value(hasItem("https://exemplo.test/um")))
                .andExpect(jsonPath("$.links").value(not(hasItem("https://exemplo.test/dois"))))
                .andExpect(jsonPath("$.extras[0].section").value("GPA"))
                .andExpect(jsonPath("$.extras.length()").value(1));
    }

    @Test
    void linkExtraDoItemNaoAceitaJavascript() throws Exception {
        String token = newUser();
        postJson(token, "/api/v1/profiles", """
                {"name":"X","seniority":"JUNIOR","entries":[{"kind":"PROJECT","title":"T","extraUrls":["javascript:alert(1)"]}]}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void excluirContaExigeSenhaEApagaOsDados() throws Exception {
        String token = newUser();
        postJson(token, "/api/v1/profiles", PROFILE).andExpect(status().isCreated());
        postJson(token, "/api/v1/me/delete", "{\"currentPassword\":\"senha-errada-123456\"}").andExpect(status().is4xxClientError());
        getJson(token, "/api/v1/profiles").andExpect(status().isOk());
        postJson(token, "/api/v1/me/delete", "{\"currentPassword\":\"" + PASSWORD + "\"}").andExpect(status().isNoContent());
        getJson(token, "/api/v1/profiles").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void idiomaDaVagaForaDaListaBloqueia() {
        var filters = new dev.jobdragonquest.profiles.ProfileSnapshot.Filters(java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.Set.of(), java.util.Set.of(), null, null, null, java.util.Set.of("pt"));
        org.assertj.core.api.Assertions.assertThat(dev.jobdragonquest.matching.PreferenceFilters.hardBlockers(filters, "A", "Engineer",
                "You will work with the team and our product, and experience for the role are welcome to you"))
                .extracting(b -> b.code()).contains("JOB_LANGUAGE");
    }

    @Test
    void metodoNaoAceitoResponde405() throws Exception {
        String token = newUser();
        String profile = read(postJson(token, "/api/v1/profiles", PROFILE).andExpect(status().isCreated()), "$.id");
        postJson(token, "/api/v1/profiles/" + profile + "/resume", "{}").andExpect(status().isMethodNotAllowed());
    }

    @Test
    void resumeAndLetterUseOnlyProfileDataAndHighlightWhatTheJobAsks() throws Exception {
        String token = newUser();
        String profile = read(postJson(token, "/api/v1/profiles", PROFILE).andExpect(status().isCreated()), "$.id");
        String job = job(token, "1", "Desenvolvedora Java", "", "Spring Boot", "Kotlin");

        getJson(token, "/api/v1/profiles/" + profile + "/resume?jobId=" + job)
                .andExpect(jsonPath("$.headline").value("Desenvolvedora back-end Java"))
                .andExpect(jsonPath("$.skills[0].name").value("Spring Boot"))
                .andExpect(jsonPath("$.skills[0].highlighted").value(true))
                .andExpect(jsonPath("$.skills[*].name").value(not(hasItem("Kotlin"))))
                .andExpect(jsonPath("$.experience[0].highlighted").value(true))
                .andExpect(jsonPath("$.education[0].title").value("Análise e Desenvolvimento de Sistemas"))
                .andExpect(jsonPath("$.company").value("Empresa 1"));
        getJson(token, "/api/v1/profiles/" + profile + "/resume").andExpect(jsonPath("$.skills[0].name").value("Java"));

        getJson(token, "/api/v1/profiles/" + profile + "/cover-letter?jobId=" + job)
                .andExpect(jsonPath("$.text").value(containsString("equipe da Empresa 1")))
                .andExpect(jsonPath("$.text").value(containsString("Spring Boot (8 meses)")))
                .andExpect(jsonPath("$.text").value(not(containsString("Kotlin"))))
                .andExpect(jsonPath("$.text").value(containsString("aviso prévio é de 15 dias")));
    }

    @Test
    void savedLetterTemplateAndMissingData() throws Exception {
        String token = newUser();
        String withLetter = read(postJson(token, "/api/v1/profiles", """
                {"name":"Carta","seniority":"JUNIOR","coverLetter":"Quero trabalhar na {empresa} como {cargo}."}"""), "$.id");
        String job = job(token, "2", "Analista de Dados", "");
        getJson(token, "/api/v1/profiles/" + withLetter + "/cover-letter?jobId=" + job)
                .andExpect(jsonPath("$.text").value("Quero trabalhar na Empresa 2 como Analista de Dados."));
        String empty = read(postJson(token, "/api/v1/profiles", """
                {"name":"Vazio","seniority":"JUNIOR"}"""), "$.id");
        getJson(token, "/api/v1/profiles/" + empty + "/cover-letter")
                .andExpect(jsonPath("$.missing").value(hasItem("resumo profissional")))
                .andExpect(jsonPath("$.missing").value(hasItem("competências")));
    }

    @Test
    void interviewSalaryAndSkillsComeFromTheAccount() throws Exception {
        String token = newUser();
        String profile = read(postJson(token, "/api/v1/profiles", PROFILE), "$.id");
        String job = job(token, "3", "Desenvolvedor Java", "\"salaryMin\":4000,\"salaryMax\":6000,\"salaryCurrency\":\"BRL\",\"salaryPeriod\":\"MONTH\",",
                "Spring Boot", "Kotlin");
        job(token, "4", "Desenvolvedora Java Pleno", "\"salaryMin\":7000,\"salaryMax\":9000,\"salaryCurrency\":\"BRL\",\"salaryPeriod\":\"MONTH\",",
                "Kotlin", "Docker");
        job(token, "5", "Analista de Marketing", "\"salaryMax\":3000,\"salaryCurrency\":\"BRL\",\"salaryPeriod\":\"MONTH\",", "SEO");

        getJson(token, "/api/v1/jobs/" + job + "/interview?profileId=" + profile)
                .andExpect(jsonPath("$[?(@.category == 'technical')].hint").value(hasItem(containsString("Estágio em desenvolvimento"))))
                .andExpect(jsonPath("$[?(@.category == 'gap')].question").value(hasItem("Você tem experiência com Kotlin?")))
                .andExpect(jsonPath("$[?(@.category == 'salary')].hint").value(hasItem(containsString("5000 BRL"))));

        getJson(token, "/api/v1/career/salary?title=desenvolvedor java")
                .andExpect(jsonPath("$[0].jobs").value(2))
                .andExpect(jsonPath("$[0].min").value(5000))
                .andExpect(jsonPath("$[0].max").value(8000))
                .andExpect(jsonPath("$[0].median").value(6500));

        getJson(token, "/api/v1/career/skills?profileId=" + profile + "&title=java")
                .andExpect(jsonPath("$[0].skill").value("kotlin"))
                .andExpect(jsonPath("$[0].percent").value(100))
                .andExpect(jsonPath("$[0].inProfile").value(false))
                .andExpect(jsonPath("$[?(@.skill == 'spring boot')].inProfile").value(hasItem(true)));
    }

    @Test
    void otherAccountsCannotReadCareerData() throws Exception {
        String token = newUser();
        String profile = read(postJson(token, "/api/v1/profiles", PROFILE), "$.id");
        String intruder = newUser();
        getJson(intruder, "/api/v1/profiles/" + profile + "/resume").andExpect(status().isNotFound());
        getJson(intruder, "/api/v1/profiles/" + profile + "/cover-letter").andExpect(status().isNotFound());
        getJson(intruder, "/api/v1/career/skills?profileId=" + profile).andExpect(status().isNotFound());
        getJson(intruder, "/api/v1/career/salary").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
