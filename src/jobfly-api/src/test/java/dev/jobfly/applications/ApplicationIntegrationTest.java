package dev.jobfly.applications;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import dev.jobfly.ApiTestSupport;

/** Cenários da seção 29 do prompt que cabem neste incremento (registro externo, deduplicação, evidências). */
class ApplicationIntegrationTest extends ApiTestSupport {

    private String create(String token, String json) throws Exception {
        return read(postJson(token, "/api/v1/applications", json).andExpect(status().isCreated()), "$.id");
    }

    private String transition(String token, String id, String json) throws Exception {
        return postJson(token, "/api/v1/applications/" + id + "/transitions", json).andReturn().getResponse().getContentAsString();
    }

    @Test
    void registersExternalApplicationsFromAnyOrigin() throws Exception {
        String token = newUser();
        // 29.1 LinkedIn, 29.2 site da empresa, 29.3 Instagram, 29.4 grupo do Facebook
        create(token, """
                {"origin":"MANUAL","platform":"LinkedIn","company":"Sensedia","title":"Desenvolvedor Java Júnior",
                 "url":"https://www.linkedin.com/jobs/view/4486065865/","appliedAt":"2026-11-04T15:00:00Z"}""");
        create(token, """
                {"origin":"MANUAL","platform":"Site da empresa","company":"Avanade","title":"Desenvolvedor(a) BackEnd",
                 "url":"https://careers.avanade.com/job/123"}""");
        create(token, """
                {"origin":"SOCIAL","platform":"Instagram","company":"Tech Recife","title":"Estágio em Desenvolvimento",
                 "url":"https://www.instagram.com/p/ABC123/?igshid=xyz","notes":"Currículo enviado por e-mail, conforme a publicação"}""");
        create(token, """
                {"origin":"SOCIAL","platform":"Facebook","company":"Startup PE","title":"Dev Java Júnior"}""");

        var list = getJson(token, "/api/v1/applications?size=10").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].state", hasItem("SUBMITTED")));
        getJson(token, "/api/v1/applications?platform=instagram")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].url").value("https://instagram.com/p/ABC123"))
                .andExpect(jsonPath("$.content[0].appliedAt").doesNotExist());
        getJson(token, "/api/v1/applications?company=sensedia")
                .andExpect(jsonPath("$.content[0].appliedAt").value("2026-11-04T15:00:00Z"));
        list.andExpect(jsonPath("$.content[0].stale").value(false));
    }

    @Test
    void preventsDuplicatesByUrlCompanyTitleAndIdempotencyKey() throws Exception {
        String token = newUser();
        String id = create(token, """
                {"origin":"MANUAL","company":"CI&T","title":"Developer Fullstack Jr","url":"https://ciandt.com/jobs/32142?utm_source=x"}""");

        postJson(token, "/api/v1/applications", """
                {"origin":"EMAIL","company":"Outra","title":"Outro","url":"https://www.ciandt.com/jobs/32142"}""")
                .andExpect(status().isOk()).andExpect(header().string("X-Duplicate-Of", id));
        postJson(token, "/api/v1/applications", """
                {"origin":"MANUAL","company":"CI&T S.A.","title":"developer fullstack JR"}""")
                .andExpect(status().isOk()).andExpect(header().string("X-Duplicate-Of", id));

        String body = """
                {"origin":"BROWSER_EXTENSION","company":"Liferay","title":"Associate Customer Support Engineer"}""";
        var first = mvc.perform(auth(post("/api/v1/applications"), token).header("Idempotency-Key", "ext-req-000001")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(auth(post("/api/v1/applications"), token).header("Idempotency-Key", "ext-req-000001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(read(first, "$.id")));
        mvc.perform(auth(post("/api/v1/applications"), token).header("Idempotency-Key", "x'; drop")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_idempotency_key"));
    }

    @Test
    void linksJobAndProfileAndComputesScore() throws Exception {
        String token = newUser();
        String profileId = read(postJson(token, "/api/v1/profiles", JAVA_PROFILE), "$.id");
        String jobId = read(postJson(token, "/api/v1/jobs", """
                {"source":"GUPY","company":"EvoluServices","title":"Analista Backend Java Júnior","workMode":"REMOTE",
                 "url":"https://evoluservices.gupy.io/jobs/1","requirements":[{"kind":"MANDATORY","text":"Java","skill":"java"}]}"""), "$.id");

        postJson(token, "/api/v1/applications", """
                {"origin":"AUTOMATION","jobId":"%s","profileId":"%s"}""".formatted(jobId, profileId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company").value("EvoluServices"))
                .andExpect(jsonPath("$.url").value("https://evoluservices.gupy.io/jobs/1"))
                .andExpect(jsonPath("$.state").value("JOB_FOUND"))
                .andExpect(jsonPath("$.matchScore").value(100));

        postJson(token, "/api/v1/applications", """
                {"origin":"MANUAL"}""").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("company_title_required"));
    }

    @Test
    void evidenceDrivenStateChangesWithSuggestionsAndNoInventedRejection() throws Exception {
        String token = newUser();
        String id = create(token, """
                {"origin":"MANUAL","platform":"LinkedIn","company":"Segsat","title":"Estágio em TI"}""");

        // Confirmação automática leva no máximo a CONFIRMED (RN-04).
        transition(token, id, """
                {"toState":"CONFIRMED","source":"EMAIL","confidence":0.95,"evidence":"Recebemos sua candidatura"}""");
        // Mesma etapa: só registra a evidência.
        postJson(token, "/api/v1/applications/" + id + "/transitions", """
                {"toState":"CONFIRMED","source":"EMAIL","confidence":0.9,"evidence":"Seu currículo segue em análise"}""")
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("NOTED"));

        // Confiança baixa vira sugestão: a etapa não muda.
        var suggested = postJson(token, "/api/v1/applications/" + id + "/transitions", """
                {"toState":"INTERVIEW_SCHEDULED","source":"AGENT","confidence":0.55,
                 "evidence":"E-mail menciona 'conversa' sem data"}""")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("SUGGESTED"))
                .andExpect(jsonPath("$.application.state").value("CONFIRMED"));
        String suggestionId = read(suggested, "$.event.id");

        getJson(token, "/api/v1/dashboard/pending")
                .andExpect(jsonPath("$[*].type", hasItem("SUGGESTION_TO_REVIEW")))
                // Campos estruturados para o painel montar a frase no idioma escolhido (issue #38).
                .andExpect(jsonPath("$[?(@.type=='SUGGESTION_TO_REVIEW')].suggestedState", hasItem("INTERVIEW_SCHEDULED")))
                .andExpect(jsonPath("$[?(@.type=='SUGGESTION_TO_REVIEW')].confidence", hasItem(0.55)));

        postJson(token, "/api/v1/applications/" + id + "/suggestions/" + suggestionId + "/accept", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.state").value("INTERVIEW_SCHEDULED"));
        postJson(token, "/api/v1/applications/" + id + "/suggestions/" + suggestionId + "/accept", "")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("suggestion_resolved"));

        // Fonte automática não volta etapa, e confiança é obrigatória para ela.
        postJson(token, "/api/v1/applications/" + id + "/transitions", """
                {"toState":"SUBMITTED","source":"EMAIL","confidence":0.99,"evidence":"x"}""")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_transition"));
        postJson(token, "/api/v1/applications/" + id + "/transitions", """
                {"toState":"REJECTED","source":"EMAIL","evidence":"x"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("confidence_required"));

        // Reprovação real, com evidência. Depois disso, nada automático reabre.
        transition(token, id, """
                {"toState":"REJECTED","source":"EMAIL","confidence":0.97,"evidence":"Optamos por seguir com outros candidatos"}""");
        postJson(token, "/api/v1/applications/" + id + "/transitions", """
                {"toState":"SCREENING","source":"PORTAL","confidence":1,"evidence":"Status do portal"}""")
                .andExpect(status().isConflict());

        var detail = getJson(token, "/api/v1/applications/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.application.state").value("REJECTED"))
                .andExpect(jsonPath("$.events.length()").value(6));
        detail.andExpect(jsonPath("$.events[2].kind").value("NOTE"))
                .andExpect(jsonPath("$.events[3].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.events[4].source").value("MANUAL"));

        // A pessoa pode reabrir.
        postJson(token, "/api/v1/applications/" + id + "/transitions", """
                {"toState":"SCREENING","source":"MANUAL","evidence":"A recrutadora me ligou: o processo foi reaberto"}""")
                .andExpect(status().isOk()).andExpect(jsonPath("$.event.confidence").value(1));
    }

    @Test
    void notesSuggestionsDismissalUpdateAndFutureDates() throws Exception {
        String token = newUser();
        String id = create(token, """
                {"origin":"REFERRAL","company":"Accenture","title":"Desenvolvedora Java"}""");

        postJson(token, "/api/v1/applications/" + id + "/notes", """
                {"text":"Indicação feita por colega do hackathon"}""").andExpect(status().isCreated());

        String suggestionId = read(postJson(token, "/api/v1/applications/" + id + "/transitions", """
                {"toState":"TECH_TEST_PENDING","source":"EMAIL","confidence":0.4,"evidence":"Talvez um teste"}"""), "$.event.id");
        postJson(token, "/api/v1/applications/" + id + "/suggestions/" + suggestionId + "/dismiss", "")
                .andExpect(status().isNoContent());

        String future = Instant.now().plus(2, ChronoUnit.DAYS).toString();
        postJson(token, "/api/v1/applications/" + id + "/transitions", """
                {"toState":"SCREENING","source":"MANUAL","evidence":"x","occurredAt":"%s"}""".formatted(future))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("future_date"));

        putJson(token, "/api/v1/applications/" + id, """
                {"company":"Accenture","title":"Desenvolvedora Java","platform":"Indicação","nextAction":"Enviar portfólio",
                 "nextDueAt":"%s","version":0}""".formatted(future))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nextAction").value("Enviar portfólio"));
        putJson(token, "/api/v1/applications/" + id, """
                {"company":"Accenture","title":"Desenvolvedora Java","version":0}""")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("stale_version"));

        getJson(token, "/api/v1/dashboard/pending").andExpect(jsonPath("$[0].type").value("DEADLINE_SOON"))
                .andExpect(jsonPath("$[0].message").value("Enviar portfólio"))
                .andExpect(jsonPath("$[0].detail").value("Enviar portfólio"));
    }

    @Test
    void boardAndSummaryShowEverythingInOnePlace() throws Exception {
        String token = newUser();
        String a = create(token, """
                {"origin":"MANUAL","company":"Nubank","title":"Software Engineer Intern"}""");
        create(token, """
                {"origin":"MANUAL","company":"iFood","title":"Dev Júnior","state":"AWAITING_USER","nextAction":"Concluir o teste DigAÍ"}""");
        transition(token, a, """
                {"toState":"TECH_TEST_PENDING","source":"MANUAL","evidence":"Convite para o teste técnico"}""");

        getJson(token, "/api/v1/applications/board").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(ApplicationState.values().length))
                .andExpect(jsonPath("$[?(@.state=='TECH_TEST_PENDING')].count").value(hasItem(1)))
                .andExpect(jsonPath("$[?(@.state=='AWAITING_USER')].cards[0].company").value(hasItem("iFood")));

        getJson(token, "/api/v1/dashboard/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationsTotal").value(2))
                .andExpect(jsonPath("$.submitted").value(1))
                .andExpect(jsonPath("$.testsPending").value(1))
                .andExpect(jsonPath("$.pendingItems").value(2));

        getJson(token, "/api/v1/dashboard/pending")
                .andExpect(jsonPath("$[*].type", hasItem("ACTION_REQUIRED")))
                .andExpect(jsonPath("$[*].type", hasItem("TECH_TEST")));
    }

    @Test
    void anotherUserCannotTouchApplications() throws Exception {
        String owner = newUser();
        String intruder = newUser();
        String id = create(owner, """
                {"origin":"MANUAL","company":"Amazon","title":"SDE Intern"}""");

        getJson(intruder, "/api/v1/applications/" + id).andExpect(status().isNotFound());
        postJson(intruder, "/api/v1/applications/" + id + "/transitions", """
                {"toState":"REJECTED","source":"MANUAL","evidence":"x"}""").andExpect(status().isNotFound());
        postJson(intruder, "/api/v1/applications/" + id + "/notes", "{\"text\":\"x\"}").andExpect(status().isNotFound());
        deleteJson(intruder, "/api/v1/applications/" + id).andExpect(status().isNotFound());
        getJson(intruder, "/api/v1/applications").andExpect(jsonPath("$.totalElements").value(0));
        // Associar perfil de outra pessoa também é recusado.
        String foreignProfile = read(postJson(owner, "/api/v1/profiles", JAVA_PROFILE), "$.id");
        postJson(intruder, "/api/v1/applications", """
                {"origin":"MANUAL","company":"X","title":"Y","profileId":"%s"}""".formatted(foreignProfile))
                .andExpect(status().isNotFound());

        deleteJson(owner, "/api/v1/applications/" + id).andExpect(status().isNoContent());
    }
}
