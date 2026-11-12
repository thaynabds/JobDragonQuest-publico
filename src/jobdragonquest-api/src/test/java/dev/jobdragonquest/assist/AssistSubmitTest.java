package dev.jobdragonquest.assist;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;

/** Envio confirmado pela extensão: a candidatura preparada da vaga salva passa para enviada, com evidência. */
class AssistSubmitTest extends ApiTestSupport {

    private static final String URL = "https://empresa.test/vagas/77";

    private String savedJob(String token) throws Exception {
        return read(postJson(token, "/api/v1/jobs", """
                {"source":"MANUAL","url":"%s","company":"Submit SA","title":"Desenvolvedora Java","workMode":"REMOTE"}"""
                .formatted(URL)).andExpect(status().isCreated()), "$.id");
    }

    private String preparedApplication(String token, String jobId) throws Exception {
        return read(postJson(token, "/api/v1/applications", """
                {"origin":"AUTOMATION","jobId":"%s","state":"PREPARED","company":"Submit SA","title":"Desenvolvedora Java",
                 "url":"%s"}""".formatted(jobId, URL)).andExpect(status().isCreated()), "$.id");
    }

    @Test
    void confirmedSubmissionMovesThePreparedApplicationToSubmitted() throws Exception {
        String token = newUser();
        String applicationId = preparedApplication(token, savedJob(token));

        postJson(token, "/api/v1/assist/submitted", "{\"url\":\"" + URL + "\",\"evidence\":\"Enviada pela extensão\"}")
                .andExpect(status().isNoContent());

        getJson(token, "/api/v1/applications/" + applicationId)
                .andExpect(jsonPath("$.application.state").value("SUBMITTED"));
    }

    @Test
    void missingApplicationOtherAccountsAndMissingEvidenceAreRejected() throws Exception {
        String owner = newUser();
        String jobId = savedJob(owner);
        // Sem candidatura preparada para a vaga: não há o que registrar como enviado.
        postJson(owner, "/api/v1/assist/submitted", "{\"url\":\"" + URL + "\",\"evidence\":\"x\"}").andExpect(status().isNotFound());

        String applicationId = preparedApplication(owner, jobId);
        String intruder = newUser();
        postJson(intruder, "/api/v1/assist/submitted", "{\"url\":\"" + URL + "\",\"evidence\":\"x\"}").andExpect(status().isNotFound());
        postJson(owner, "/api/v1/assist/submitted", "{\"url\":\"" + URL + "\",\"evidence\":\"\"}").andExpect(status().is4xxClientError());

        getJson(owner, "/api/v1/applications/" + applicationId)
                .andExpect(jsonPath("$.application.state").value("PREPARED"));
    }
}
