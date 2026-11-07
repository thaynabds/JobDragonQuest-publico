package dev.jobdragonquest.assist;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;

/** Perguntas e respostas e revisão antes de enviar (issue #115): só dados reais do perfil. */
class AssistTest extends ApiTestSupport {

    private String job(String token) throws Exception {
        return read(postJson(token, "/api/v1/jobs", """
                {"source":"MANUAL","url":"https://empresa.test/vagas/77","company":"Assist SA","title":"Desenvolvedora Java",
                 "workMode":"REMOTE","requirements":[{"kind":"MANDATORY","text":"Java","skill":"Java","minYears":2},
                                                     {"kind":"MANDATORY","text":"Kotlin","skill":"Kotlin"}]}""")
                .andExpect(status().isCreated()), "$.id");
    }

    @Test
    void answersComeFromTheProfileAndMissingDataIsFlaggedNotInvented() throws Exception {
        String token = newUser();
        String profile = read(postJson(token, "/api/v1/profiles", """
                {"name":"Java","seniority":"JUNIOR","workModes":["REMOTE"],"salaryExpectation":5000,"salaryCurrency":"BRL",
                 "salaryPeriod":"MONTHLY","noticePeriodDays":30,"city":"Recife","country":"BR","linkedinUrl":"https://linkedin.com/in/teste",
                 "skills":[{"name":"Java","level":4,"years":1,"months":6}],
                 "answers":[{"question":"Por que esta empresa?","answer":"Gosto do produto."}]}"""), "$.id");
        String job = job(token);
        getJson(token, "/api/v1/jobs/" + job + "/assist/answers?profileId=" + profile)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.category == 'salary')].answer").value(hasItem("5000 BRL (monthly)")))
                .andExpect(jsonPath("$[?(@.category == 'start')].answer").value(hasItem("Aviso prévio de 30 dias")))
                .andExpect(jsonPath("$[?(@.category == 'experience')].answer").value(hasItem("1 ano, 6 meses")))
                .andExpect(jsonPath("$[?(@.category == 'experience_gap')].missing").value(hasItem(org.hamcrest.Matchers.containsString("tempo real"))))
                // Kotlin não está no perfil: sem resposta, com aviso para não inventar.
                .andExpect(jsonPath("$[?(@.category == 'experience')].question").value(hasItem("Quanto tempo de experiência você tem com Kotlin?")))
                .andExpect(jsonPath("$[?(@.category == 'experience')].missing").value(hasItem(org.hamcrest.Matchers.containsString("Só adicione se você realmente"))))
                .andExpect(jsonPath("$[?(@.category == 'sponsorship')].answer").value(hasItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$[?(@.category == 'saved')].answer").value(hasItem("Gosto do produto.")));
    }

    @Test
    void reviewFlagsBlockersRepeatsAndMissingData() throws Exception {
        String token = newUser();
        String profile = read(postJson(token, "/api/v1/profiles", """
                {"name":"Presencial","seniority":"JUNIOR","workModes":["ONSITE"],"skills":[{"name":"Java","level":3}]}"""), "$.id");
        String job = job(token);
        getJson(token, "/api/v1/jobs/" + job + "/assist/review?profileId=" + profile)
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.checks[*].code").value(hasItem("blocker_work_mode")))
                .andExpect(jsonPath("$.checks[*].code").value(hasItem("mandatory_unmet")))
                .andExpect(jsonPath("$.checks[*].code").value(hasItem("form_data_missing")))
                .andExpect(jsonPath("$.checks[*].code").value(hasItem("not_applied")));

        postJson(token, "/api/v1/applications", """
                {"origin":"MANUAL","jobId":"%s","company":"Assist SA","title":"Desenvolvedora Java"}""".formatted(job))
                .andExpect(status().isCreated());
        getJson(token, "/api/v1/jobs/" + job + "/assist/review?profileId=" + profile)
                .andExpect(jsonPath("$.checks[*].code").value(hasItem("already_applied")));
    }

    @Test
    void otherAccountsGetNotFound() throws Exception {
        String token = newUser();
        String profile = read(postJson(token, "/api/v1/profiles", """
                {"name":"X","seniority":"JUNIOR"}"""), "$.id");
        String job = job(token);
        String intruder = newUser();
        getJson(intruder, "/api/v1/jobs/" + job + "/assist/answers?profileId=" + profile).andExpect(status().isNotFound());
        getJson(intruder, "/api/v1/jobs/" + job + "/assist/review?profileId=" + profile).andExpect(status().isNotFound());
    }
}
