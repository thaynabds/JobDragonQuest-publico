package dev.jobdragonquest.profiles;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;

/** Critérios de aceite da issue #96: perfil profissional completo (dados fictícios). */
class ProfileCompleteIntegrationTest extends ApiTestSupport {

    private static final String FULL = """
            {"name":"Back-end Java","seniority":"SPECIALIST","headline":"Desenvolvedora Back-end | Java e Spring",
             "summary":"Resumo de teste.","currentTitle":"Desenvolvedora","city":"Cidade Teste","stateRegion":"PE","country":"BR",
             "requiresSponsorship":false,"willingToRelocate":true,"travelAvailability":"OCCASIONAL","noticePeriodDays":30,
             "availableFrom":"2026-12-01","currentSalary":4000,"salaryExpectation":6000,"salaryCurrency":"BRL","salaryPeriod":"MONTHLY",
             "linkedinUrl":"https://www.linkedin.com/in/pessoa-teste","githubUrl":"https://github.com/pessoa-teste",
             "portfolioUrl":"https://exemplo.dev","coverLetter":"Carta de teste.","disability":false,"disabilityDetails":"não deve ficar",
             "workAuthorization":["BR","PT"],"employmentTypes":["CLT","PJ","FULL_TIME"],"excludedCompanies":["Empresa X"],
             "includeKeywords":["Java"],"excludeKeywords":["PHP"],"industries":["Fintech"],
             "answers":[{"question":"Por que esta empresa?","answer":"Resposta de teste."}],
             "skills":[{"name":"Java","level":4,"years":2,"months":3,"days":10},{"name":"Spring","level":3,"years":1.5}],
             "languages":[{"name":"Inglês","level":"B2"},{"name":"español","level":"A2"},{"name":"日本語","level":"A1"},{"name":"Libras","level":"A1"}],
             "entries":[
               {"kind":"EXPERIENCE","title":"Desenvolvedora","organization":"Empresa Teste","startDate":"2024-01-01","endDate":"2025-01-01",
                "current":true,"location":"Remoto","employmentType":"CLT","skills":"Java, Spring"},
               {"kind":"EDUCATION","title":"Ciência da Computação","organization":"Universidade Teste","degree":"BACHELOR",
                "fieldOfStudy":"Computação","status":"IN_PROGRESS","startDate":"2023-02-01"},
               {"kind":"CERTIFICATION","title":"Certificação Teste","organization":"Emissor","startDate":"2025-05-01",
                "expirationDate":"2028-05-01","credentialId":"ABC-123","url":"https://exemplo.dev/credencial"},
               {"kind":"COURSE","title":"Curso Teste","hours":40},
               {"kind":"VOLUNTEER","title":"Mentoria","organization":"ONG Teste"}]}""";

    @Test
    void savesEveryProfileFieldAndConvertsTimeAndLanguages() throws Exception {
        String token = newUser();
        String id = read(postJson(token, "/api/v1/profiles", FULL).andExpect(status().isCreated()), "$.id");
        getJson(token, "/api/v1/profiles/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seniority").value("SPECIALIST"))
                .andExpect(jsonPath("$.headline").value("Desenvolvedora Back-end | Java e Spring"))
                .andExpect(jsonPath("$.country").value("BR"))
                .andExpect(jsonPath("$.travelAvailability").value("OCCASIONAL"))
                .andExpect(jsonPath("$.salaryPeriod").value("MONTHLY"))
                .andExpect(jsonPath("$.noticePeriodDays").value(30))
                .andExpect(jsonPath("$.workAuthorization", containsInAnyOrder("BR", "PT")))
                .andExpect(jsonPath("$.employmentTypes", containsInAnyOrder("CLT", "PJ", "FULL_TIME")))
                .andExpect(jsonPath("$.answers[0].question").value("Por que esta empresa?"))
                .andExpect(jsonPath("$.disability").value(false))
                .andExpect(jsonPath("$.disabilityDetails").doesNotExist())
                .andExpect(jsonPath("$.skills[0].years").value(2))
                .andExpect(jsonPath("$.skills[0].months").value(3))
                .andExpect(jsonPath("$.skills[0].days").value(10))
                .andExpect(jsonPath("$.skills[0].totalYears").value(2.3))
                .andExpect(jsonPath("$.skills[1].years").value(1))
                .andExpect(jsonPath("$.skills[1].months").value(6))
                .andExpect(jsonPath("$.languages[*].code", containsInAnyOrder("en", "es", "ja", "bzs")))
                .andExpect(jsonPath("$.entries[0].current").value(true))
                .andExpect(jsonPath("$.entries[0].endDate").doesNotExist())
                .andExpect(jsonPath("$.entries[0].employmentType").value("CLT"))
                .andExpect(jsonPath("$.entries[1].degree").value("BACHELOR"))
                .andExpect(jsonPath("$.entries[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.entries[2].credentialId").value("ABC-123"))
                .andExpect(jsonPath("$.entries[3].hours").value(40))
                .andExpect(jsonPath("$.entries[4].kind").value("VOLUNTEER"));
        getJson(token, "/api/v1/profiles").andExpect(jsonPath("$[0].headline").value("Desenvolvedora Back-end | Java e Spring"));
    }

    @Test
    void rejectsUnknownOrRepeatedLanguagesAndUnsafeValues() throws Exception {
        String token = newUser();
        String base = "{\"name\":\"%s\",\"seniority\":\"JUNIOR\",%s}";
        postJson(token, "/api/v1/profiles", base.formatted("A", "\"languages\":[{\"name\":\"Klingonês inventado\",\"level\":\"A1\"}]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("unknown_language"));
        postJson(token, "/api/v1/profiles", base.formatted("B", "\"languages\":[{\"name\":\"Inglês\",\"level\":\"A1\"},{\"name\":\"English\",\"level\":\"B1\"}]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("duplicate_language"));
        postJson(token, "/api/v1/profiles", base.formatted("C", "\"linkedinUrl\":\"javascript:alert(1)\""))
                .andExpect(status().isBadRequest());
        postJson(token, "/api/v1/profiles", base.formatted("D", "\"country\":\"br\""))
                .andExpect(status().isBadRequest());
        postJson(token, "/api/v1/profiles", base.formatted("E", "\"noticePeriodDays\":400"))
                .andExpect(status().isBadRequest());
        postJson(token, "/api/v1/profiles", base.formatted("F", "\"skills\":[{\"name\":\"Java\",\"level\":3,\"years\":1.5,\"months\":2}]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_skill_time"));
        postJson(token, "/api/v1/profiles", base.formatted("G", "\"skills\":[{\"name\":\"Java\",\"level\":3,\"months\":12}]"))
                .andExpect(status().isBadRequest());
        postJson(token, "/api/v1/profiles", base.formatted("H",
                "\"entries\":[{\"kind\":\"CERTIFICATION\",\"title\":\"X\",\"startDate\":\"2025-01-01\",\"expirationDate\":\"2024-01-01\"}]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_period"));
        postJson(token, "/api/v1/profiles", base.formatted("I", "\"disability\":true,\"disabilityDetails\":\"Detalhe de teste\""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.disabilityDetails").value("Detalhe de teste"));
    }
}
