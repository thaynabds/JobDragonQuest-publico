package dev.jobdragonquest.sources;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jobdragonquest.ApiTestSupport;

/** Critérios de aceite da issue #11: lembretes de conferência das fontes assistidas e fontes com falha. */
class SourceAssistedTest extends ApiTestSupport {

    @Autowired
    private WatchedSourceRepository repository;

    @Autowired
    private TransactionTemplate transaction;

    @Test
    void dueAssistedSourceBecomesPendingUntilChecked() throws Exception {
        String token = newUser();
        String id = read(postJson(token, "/api/v1/sources/bulk", """
                {"frequencyMinutes":1440,"sources":[
                  {"name":"Vagas Java","kind":"LINKEDIN","url":"https://www.linkedin.com/jobs/search/?keywords=java"},
                  {"name":"#EstagioTI","kind":"INSTAGRAM","url":"https://www.instagram.com/explore/tags/estagioti/"}]}"""),
                "$.created[0].id");

        getJson(token, "/api/v1/dashboard/pending")
                .andExpect(jsonPath("$[?(@.type=='SOURCE_TO_CHECK')].sourceId", hasItem(id)))
                .andExpect(jsonPath("$[?(@.sourceId=='%s')].url".formatted(id), hasItem("https://linkedin.com/jobs/search?keywords=java")));
        getJson(token, "/api/v1/dashboard/summary")
                .andExpect(jsonPath("$.sourcesWatched").value(2))
                .andExpect(jsonPath("$.sourcesToCheck").value(2));

        postJson(token, "/api/v1/sources/" + id + "/check", """
                {"jobsRegistered":2,"note":"Duas vagas júnior em Recife"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED"))
                .andExpect(jsonPath("$.message").value("Conferida: 2 vaga(s) registrada(s). Duas vagas júnior em Recife"));

        getJson(token, "/api/v1/dashboard/pending")
                .andExpect(jsonPath("$[?(@.type=='SOURCE_TO_CHECK')].sourceId", not(hasItem(id))));
        getJson(token, "/api/v1/sources/" + id)
                .andExpect(jsonPath("$.due").value(false))
                .andExpect(jsonPath("$.lastStatus").value("CHECKED"));
        getJson(token, "/api/v1/sources/" + id + "/runs").andExpect(jsonPath("$[0].found").value(2));

        // Conferir sem corpo também vale.
        String other = read(getJson(token, "/api/v1/sources"), "$[0].id");
        postJson(token, "/api/v1/sources/" + other + "/check", "").andExpect(status().isOk());
    }

    @Test
    void automaticSourceIsNotCheckedByHandAndFailuresArePending() throws Exception {
        String token = newUser();
        String id = read(postJson(token, "/api/v1/sources/bulk", """
                {"sources":[{"name":"Acme","kind":"LEVER","identifier":"acme"}]}"""), "$.created[0].id");

        postJson(token, "/api/v1/sources/" + id + "/check", "")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("automatic_source"));
        getJson(token, "/api/v1/dashboard/pending")
                .andExpect(jsonPath("$[*].type", not(hasItem("SOURCE_TO_CHECK"))));

        transaction.executeWithoutResult(status -> repository.findById(UUID.fromString(id)).orElseThrow()
                .recordFailure("Board não encontrado no ATS. Confira o identificador.", Instant.now()));

        getJson(token, "/api/v1/dashboard/pending")
                .andExpect(jsonPath("$[?(@.type=='SOURCE_FAILING')].message",
                        hasItem("A coleta falhou: Board não encontrado no ATS. Confira o identificador.")))
                .andExpect(jsonPath("$[?(@.type=='SOURCE_FAILING')].url", hasItem("https://jobs.lever.co/acme")))
                .andExpect(jsonPath("$[?(@.type=='SOURCE_FAILING')].detail",
                        hasItem("Board não encontrado no ATS. Confira o identificador.")));
        postJson(newUser(), "/api/v1/sources/" + id + "/check", "").andExpect(status().isNotFound());
    }
}
