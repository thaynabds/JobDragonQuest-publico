package dev.jobdragonquest.documents;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import dev.jobdragonquest.ApiTestSupport;

/** Issue #187: diploma e histórico normais; laudo sem extração de texto e fora do alcance de tokens de integração. */
class SensitiveDocumentTest extends ApiTestSupport {

    private String upload(String token, String profile, String kind) throws Exception {
        var file = new MockMultipartFile("file", "arquivo.txt", "text/plain", "conteudo do documento".getBytes(StandardCharsets.UTF_8));
        return read(mvc.perform(multipart("/api/v1/profiles/" + profile + "/documents").file(file).param("kind", kind).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()), "$.id");
    }

    @Test
    void medicalReportIsOnlyForTheSession() throws Exception {
        String session = newUser();
        String profile = read(postJson(session, "/api/v1/profiles", JAVA_PROFILE).andExpect(status().isCreated()), "$.id");
        String token = read(postJson(session, "/api/v1/tokens", "{\"name\":\"t\",\"days\":30}"), "$.value");
        String report = upload(session, profile, "MEDICAL_REPORT");
        mvc.perform(auth(get("/api/v1/documents/" + report + "/download"), token)).andExpect(status().isForbidden());
        getJson(token, "/api/v1/documents/" + report + "/read").andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/documents/" + report + "/download"), session)).andExpect(status().isOk());
        getJson(session, "/api/v1/documents/" + report + "/read").andExpect(jsonPath("$.text").value(""));
        getJson(newUser(), "/api/v1/documents/" + report + "/read").andExpect(status().isNotFound());
    }

    @Test
    void diplomaAndTranscriptWorkWithAToken() throws Exception {
        String session = newUser();
        String profile = read(postJson(session, "/api/v1/profiles", JAVA_PROFILE).andExpect(status().isCreated()), "$.id");
        String token = read(postJson(session, "/api/v1/tokens", "{\"name\":\"t\",\"days\":30}"), "$.value");
        String diploma = upload(session, profile, "DIPLOMA");
        upload(session, profile, "TRANSCRIPT");
        getJson(token, "/api/v1/documents/" + diploma + "/read").andExpect(jsonPath("$.text").value("conteudo do documento"));
    }
}
