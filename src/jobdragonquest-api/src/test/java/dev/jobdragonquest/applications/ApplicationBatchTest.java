package dev.jobdragonquest.applications;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;

/** Ações em lote no quadro (issue #106). */
class ApplicationBatchTest extends ApiTestSupport {

    private String create(String token, String title) throws Exception {
        return read(postJson(token, "/api/v1/applications", """
                {"origin":"MANUAL","platform":"LinkedIn","company":"Lote SA","title":"%s"}""".formatted(title))
                .andExpect(status().isCreated()), "$.id");
    }

    @Test
    void movesManyApplicationsAndIsolatesOtherAccounts() throws Exception {
        String token = newUser();
        String a = create(token, "Dev A");
        String b = create(token, "Dev B");
        String c = create(token, "Dev C");
        String intruder = newUser();
        String foreign = create(intruder, "Dev de outra conta");
        String missing = UUID.randomUUID().toString();

        postJson(token, "/api/v1/applications/batch/transitions", """
                {"ids":["%s","%s","%s","%s","%s","%s"],"toState":"CLOSED","evidence":"Vagas encerradas"}"""
                .formatted(a, b, c, a, foreign, missing))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(3))
                .andExpect(jsonPath("$.failed.length()").value(2))
                .andExpect(jsonPath("$.failed[0].code").value("not_found"))
                .andExpect(jsonPath("$.failed[1].code").value("not_found"));

        getJson(token, "/api/v1/applications/" + b).andExpect(jsonPath("$.application.state").value("CLOSED"));
        // A candidatura da outra conta não mudou.
        getJson(intruder, "/api/v1/applications/" + foreign).andExpect(jsonPath("$.application.state").value("SUBMITTED"));

        // Mesma etapa: vira anotação, não conta como mudança nem como falha.
        postJson(token, "/api/v1/applications/batch/transitions", """
                {"ids":["%s"],"toState":"CLOSED","evidence":"de novo"}""".formatted(a))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.failed.length()").value(0));
    }

    @Test
    void rejectsEmptyOrHugeBatchesAndMissingEvidence() throws Exception {
        String token = newUser();
        postJson(token, "/api/v1/applications/batch/transitions", """
                {"ids":[],"toState":"CLOSED","evidence":"x"}""").andExpect(status().isBadRequest());
        String many = IntStream.range(0, 101).mapToObj(i -> "\"" + UUID.randomUUID() + "\"").collect(Collectors.joining(","));
        postJson(token, "/api/v1/applications/batch/transitions", """
                {"ids":[%s],"toState":"CLOSED","evidence":"x"}""".formatted(many)).andExpect(status().isBadRequest());
        postJson(token, "/api/v1/applications/batch/transitions", """
                {"ids":["%s"],"toState":"CLOSED","evidence":" "}""".formatted(UUID.randomUUID())).andExpect(status().isBadRequest());
    }
}
