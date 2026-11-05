package dev.jobfly.jobs;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobfly.ApiTestSupport;

class JobAndMatchingIntegrationTest extends ApiTestSupport {

    private static final String SENSEDIA = """
            {"source":"LINKEDIN","url":"https://br.linkedin.com/jobs/view/desenvolvedor-java-junior-13938-at-sensedia-4486065865?trk=x",
             "company":"Sensedia","title":"Desenvolvedor Java Júnior (13938)","workMode":"REMOTE","seniority":"JUNIOR","country":"BR",
             "requirements":[{"kind":"MANDATORY","text":"Java","skill":"Java","minYears":1},
                             {"kind":"MANDATORY","text":"Experiência com Spring Boot","skill":null},
                             {"kind":"DESIRED","text":"Kafka","skill":"Kafka"}],
             "languages":[{"code":"en","minLevel":"B1","required":false}]}""";

    @Test
    void createDeduplicateByCanonicalUrlAndList() throws Exception {
        String token = newUser();
        String id = read(postJson(token, "/api/v1/jobs", SENSEDIA).andExpect(status().isCreated()), "$.id");

        // Mesma vaga por outra URL do LinkedIn: devolve a existente, sem criar outra.
        postJson(token, "/api/v1/jobs", SENSEDIA.replace(
                "https://br.linkedin.com/jobs/view/desenvolvedor-java-junior-13938-at-sensedia-4486065865?trk=x",
                "https://www.linkedin.com/jobs/view/4486065865/"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Duplicate-Of", id));

        getJson(token, "/api/v1/jobs").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].company").value("Sensedia"));
        getJson(token, "/api/v1/jobs/" + id).andExpect(jsonPath("$.requirements[0].skill").value("java"));
    }

    @Test
    void sameCompanyAndTitleWithoutUrlIsDuplicateButDifferentCityIsNot() throws Exception {
        String token = newUser();
        String base = """
                {"source":"INSTAGRAM","company":"Avanade Brasil Ltda","title":"Desenvolvedor(a) Back-End","location":"%s"}""";
        String id = read(postJson(token, "/api/v1/jobs", base.formatted("Recife")).andExpect(status().isCreated()), "$.id");
        postJson(token, "/api/v1/jobs", base.formatted("recife").replace("Avanade Brasil Ltda", "AVANADE BRASIL"))
                .andExpect(status().isOk()).andExpect(header().string("X-Duplicate-Of", id));
        postJson(token, "/api/v1/jobs", base.formatted("São Paulo")).andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidUrlAndSalaryRangeAndUnknownSourceFormat() throws Exception {
        String token = newUser();
        postJson(token, "/api/v1/jobs", """
                {"source":"MANUAL","company":"X","title":"Y","url":"javascript:alert(1)"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_url"));
        postJson(token, "/api/v1/jobs", """
                {"source":"MANUAL","company":"X","title":"Y","salaryMin":5000,"salaryMax":3000,"salaryCurrency":"BRL"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_salary_range"));
        postJson(token, "/api/v1/jobs", """
                {"source":"drop table;","company":"X","title":"Y"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void updateAndCloseJob() throws Exception {
        String token = newUser();
        String id = read(postJson(token, "/api/v1/jobs", SENSEDIA), "$.id");
        putJson(token, "/api/v1/jobs/" + id, SENSEDIA.replace("\"workMode\":\"REMOTE\"", "\"workMode\":\"HYBRID\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workMode").value("HYBRID"));
        mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/jobs/" + id + "/status"), token)
                        .contentType("application/json").content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void ranksProfilesAndSelectsTheBestOne() throws Exception {
        String token = newUser();
        String javaId = read(postJson(token, "/api/v1/profiles", JAVA_PROFILE), "$.id");
        postJson(token, "/api/v1/profiles", """
                {"name":"Desenvolvedora .NET","seniority":"JUNIOR","workModes":["REMOTE"],
                 "skills":[{"name":"C#","level":3},{"name":".NET","level":3}]}""").andExpect(status().isCreated());
        String jobId = read(postJson(token, "/api/v1/jobs", SENSEDIA), "$.id");

        getJson(token, "/api/v1/jobs/" + jobId + "/matches")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].profileId").value(javaId))
                // Obrigatórios 2/2 (Spring Boot inferido do texto), desejável Kafka 0/1: 0,7×1 + 0,3×0 = 70.
                .andExpect(jsonPath("$[0].score").value(70))
                .andExpect(jsonPath("$[0].recommendation").value("APPLY"))
                .andExpect(jsonPath("$[0].requirements[1].outcome").value("MET"))
                .andExpect(jsonPath("$[0].requirements[1].skill").value("spring boot"))
                .andExpect(jsonPath("$[0].requirements[2].outcome").value("UNMET"))
                .andExpect(jsonPath("$[0].blockers").isEmpty())
                .andExpect(jsonPath("$[1].profileName").value("Desenvolvedora .NET"))
                .andExpect(jsonPath("$[1].recommendation").value("SKIP"));

        getJson(token, "/api/v1/jobs/" + jobId + "/matches/" + javaId).andExpect(status().isOk())
                .andExpect(jsonPath("$.titleAffinity").value(true));
    }

    @Test
    void otherUsersJobsAndProfilesAreInvisible() throws Exception {
        String owner = newUser();
        String intruder = newUser();
        String jobId = read(postJson(owner, "/api/v1/jobs", SENSEDIA), "$.id");
        String profileId = read(postJson(owner, "/api/v1/profiles", JAVA_PROFILE), "$.id");

        getJson(intruder, "/api/v1/jobs/" + jobId).andExpect(status().isNotFound());
        getJson(intruder, "/api/v1/jobs/" + jobId + "/matches").andExpect(status().isNotFound());
        String intruderJob = read(postJson(intruder, "/api/v1/jobs", SENSEDIA), "$.id");
        getJson(intruder, "/api/v1/jobs/" + intruderJob + "/matches/" + profileId).andExpect(status().isNotFound());
    }
}
