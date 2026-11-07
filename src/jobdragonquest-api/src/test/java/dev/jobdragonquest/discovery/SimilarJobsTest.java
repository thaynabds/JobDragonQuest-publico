package dev.jobdragonquest.discovery;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import dev.jobdragonquest.ApiTestSupport;

/** Favoritos e vagas parecidas (issue #110). */
class SimilarJobsTest extends ApiTestSupport {

    private String job(String token, String n, String title, String... skills) throws Exception {
        StringBuilder requirements = new StringBuilder();
        for (String skill : skills) {
            if (requirements.length() > 0) {
                requirements.append(',');
            }
            requirements.append("{\"kind\":\"MANDATORY\",\"text\":\"%s\",\"skill\":\"%s\"}".formatted(skill, skill));
        }
        return read(postJson(token, "/api/v1/jobs", """
                {"source":"MANUAL","url":"https://empresa.test/vagas/%s","company":"Empresa %s","title":"%s",
                 "requirements":[%s]}""".formatted(n, n, title, requirements)).andExpect(status().isCreated()), "$.id");
    }

    private void favorite(String token, String id, boolean value) throws Exception {
        mvc.perform(put("/api/v1/jobs/" + id + "/favorite").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":" + value + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.favorite").value(value));
    }

    @Test
    void findsJobsLikeFavoritesWithExplanationAndThreshold() throws Exception {
        String token = newUser();
        String base = job(token, "1", "Desenvolvedora Java Júnior", "Java", "Spring Boot", "SQL");
        String close = job(token, "2", "Desenvolvedor Java Pleno", "Java", "Spring Boot", "Docker");
        String far = job(token, "3", "Analista de Marketing Digital", "SEO");
        String applied = job(token, "4", "Desenvolvedor Java", "Java", "Spring Boot", "SQL");
        postJson(token, "/api/v1/applications", """
                {"origin":"MANUAL","jobId":"%s","company":"Empresa 4","title":"Desenvolvedor Java"}""".formatted(applied))
                .andExpect(status().isCreated());

        // Sem favoritos, não há base: lista vazia.
        getJson(token, "/api/v1/jobs/similar").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        favorite(token, base, true);
        getJson(token, "/api/v1/jobs?favorite=true").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(base));

        // A parecida aparece com explicação; a de marketing fica abaixo do mínimo; a já candidatada fica de fora.
        getJson(token, "/api/v1/jobs/similar?basis=FAVORITES&minScore=30")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].job.id").value(close))
                .andExpect(jsonPath("$[0].basisJobId").value(base))
                .andExpect(jsonPath("$[0].sharedWords[*]").value(org.hamcrest.Matchers.hasItems("desenvolvedor", "java")))
                .andExpect(jsonPath("$[0].sharedSkills[*]").value(org.hamcrest.Matchers.hasItem("java")))
                .andExpect(jsonPath("$[0].score").value(org.hamcrest.Matchers.greaterThanOrEqualTo(30)));
        // Mínimo 0 mostra também a pouco parecida? Não: semelhança zero nunca aparece.
        getJson(token, "/api/v1/jobs/similar?minScore=0").andExpect(jsonPath("$[*].job.id").value(
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(far))));
        getJson(token, "/api/v1/jobs/similar?minScore=101").andExpect(status().isBadRequest());

        // Base pelas candidaturas enviadas: a vaga candidatada vira base e a parecida aparece.
        favorite(token, base, false);
        getJson(token, "/api/v1/jobs/similar?basis=APPLIED&minScore=30")
                .andExpect(jsonPath("$[*].job.id").value(org.hamcrest.Matchers.hasItems(close, base)))
                .andExpect(jsonPath("$[0].basisJobId").value(applied));
    }

    @Test
    void favoritesAndSimilarityAreIsolatedPerAccount() throws Exception {
        String owner = newUser();
        String id = job(owner, "9", "Desenvolvedora Python", "Python");
        String intruder = newUser();
        mvc.perform(put("/api/v1/jobs/" + id + "/favorite").header("Authorization", "Bearer " + intruder)
                .contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":true}")).andExpect(status().isNotFound());
        job(intruder, "10", "Desenvolvedora Python Sênior", "Python");
        favorite(owner, id, true);
        // A busca do dono não enxerga vagas da outra conta.
        getJson(owner, "/api/v1/jobs/similar?minScore=0").andExpect(jsonPath("$.length()").value(0));
    }
}
