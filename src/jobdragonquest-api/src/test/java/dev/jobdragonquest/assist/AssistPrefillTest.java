package dev.jobdragonquest.assist;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;

/** Preenchimento pela extensão (issue #234): dados da vaga salva, do perfil que mais combina e do contato do perfil. */
class AssistPrefillTest extends ApiTestSupport {

    private static final String URL = "https://empresa.test/vagas/88";

    private String savedJob(String token) throws Exception {
        return read(postJson(token, "/api/v1/jobs", """
                {"source":"MANUAL","url":"%s","company":"Prefill SA","title":"Desenvolvedora Java",
                 "workMode":"REMOTE","requirements":[{"kind":"MANDATORY","text":"Java","skill":"Java","minYears":2}]}""".formatted(URL))
                .andExpect(status().isCreated()), "$.id");
    }

    @Test
    void prefillUsesTheSavedJobTheBestProfileAndTheProfileContactOnly() throws Exception {
        String token = newUser();
        postJson(token, "/api/v1/profiles", """
                {"name":"Java","seniority":"JUNIOR","workModes":["REMOTE"],"salaryExpectation":5000,"salaryCurrency":"BRL",
                 "salaryPeriod":"MONTHLY","noticePeriodDays":30,"city":"Recife","country":"BR",
                 "skills":[{"name":"Java","level":4,"years":1,"months":6}],
                 "options":{"socialName":"Nome Teste","contactEmail":"contato@teste.example","phone":"999999999"}}""");
        savedJob(token);

        postJson(token, "/api/v1/assist/prefill", "{\"url\":\"" + URL + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company").value("Prefill SA"))
                .andExpect(jsonPath("$.name").value("Nome Teste"))
                .andExpect(jsonPath("$.email").value("contato@teste.example"))
                .andExpect(jsonPath("$.phone").value("999999999"))
                .andExpect(jsonPath("$.answers[?(@.category == 'salary')].answer").value(hasItem("5000 BRL (monthly)")))
                .andExpect(jsonPath("$.answers[?(@.category == 'experience')].answer").value(hasItem("1 ano, 6 meses")));
    }

    @Test
    void anotherAccountsJobAndUnknownOrInvalidUrlsAreNotFoundOrRejected() throws Exception {
        String owner = newUser();
        postJson(owner, "/api/v1/profiles", "{\"name\":\"Java\",\"seniority\":\"JUNIOR\",\"skills\":[{\"name\":\"Java\",\"level\":3}]}");
        savedJob(owner);

        String other = newUser();
        postJson(other, "/api/v1/assist/prefill", "{\"url\":\"" + URL + "\"}").andExpect(status().isNotFound());
        postJson(owner, "/api/v1/assist/prefill", "{\"url\":\"https://empresa.test/vagas/outra\"}").andExpect(status().isNotFound());
        postJson(owner, "/api/v1/assist/prefill", "{\"url\":\"javascript:alert(1)\"}").andExpect(status().is4xxClientError());
    }
}
