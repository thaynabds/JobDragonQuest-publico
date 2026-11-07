package dev.jobdragonquest.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import dev.jobdragonquest.ApiTestSupport;

/**
 * Issue #131: todo erro da API, venha do controller, da validação, do filtro de tamanho ou da segurança, segue o mesmo
 * esquema Problem Details (RFC 9457) com {@code title}, {@code status}, {@code detail}, {@code instance} e o {@code code}
 * estável que o painel traduz; {@code type} fica ausente, o que pela RFC equivale a {@code about:blank}. Nenhum corpo de erro expõe stack trace, SQL ou nome de classe.
 */
class ErrorFormatTest extends ApiTestSupport {

    private static void assertProblem(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.instance").value(org.hamcrest.Matchers.startsWith("/")))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.code").value(code));
        String body = result.andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception", "at dev.", "SELECT ", "trace");
    }

    @Test
    void validationErrorListsTheFields() throws Exception {
        String token = newUser();
        ResultActions result = postJson(token, "/api/v1/profiles", "{\"name\":\"\"}");
        assertProblem(result, 400, "validation_failed");
        result.andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void malformedJson() throws Exception {
        assertProblem(postJson(newUser(), "/api/v1/profiles", "{nao é json"), 400, "malformed_request");
    }

    @Test
    void resourceOfAnotherUserIsNotFound() throws Exception {
        assertProblem(getJson(newUser(), "/api/v1/profiles/" + UUID.randomUUID()), 404, "not_found");
    }

    @Test
    void missingTokenIsUnauthorizedWithBearerChallenge() throws Exception {
        ResultActions result = mvc.perform(get("/api/v1/profiles"));
        assertProblem(result, 401, "unauthorized");
        result.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")));
    }

    @Test
    void invalidTokenIsUnauthorizedWithoutSayingWhy() throws Exception {
        ResultActions result = mvc.perform(get("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, "Bearer nao-e-um-token"));
        assertProblem(result, 401, "unauthorized");
        assertThat(result.andReturn().getResponse().getContentAsString()).doesNotContain("Jwt", "nao-e-um-token");
    }

    @Test
    void adminRouteIsForbiddenForAnOrdinaryUser() throws Exception {
        assertProblem(getJson(newUser(), "/actuator/metrics"), 403, "forbidden");
    }

    @Test
    void bodyAboveTheLimitIsRejectedInTheSameFormat() throws Exception {
        ResultActions result = mvc.perform(auth(post("/api/v1/profiles"), newUser())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + "x".repeat(2 * 1024 * 1024) + "\"}"));
        assertProblem(result, 413, "payload_too_large");
    }

    @Test
    void jsonEscapingKeepsTheBodyValid() {
        assertThat(ProblemResponses.json("a\"b\\c<d\n")).isEqualTo("a\\\"b\\\\c\\u003cd\\u000a");
    }
}
