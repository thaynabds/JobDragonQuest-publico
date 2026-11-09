package dev.jobdragonquest.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;
import dev.jobdragonquest.sources.collect.AtsPaths;

/** Issue #172: identificador livre, frequência em horas ou dias e URL da fonte preservada. */
class SourceOptionsTest extends ApiTestSupport {

    private String profile(String token) throws Exception {
        return read(postJson(token, "/api/v1/profiles", JAVA_PROFILE).andExpect(status().isCreated()), "$.id");
    }

    @Test
    void identifierAcceptsAnyTextAndStaysInOnePathSegment() throws Exception {
        String token = newUser();
        postJson(token, "/api/v1/sources/bulk", """
                {"profileIds":["%s"],"frequencyMinutes":14400,
                 "sources":[{"name":"Dragão","kind":"GREENHOUSE","identifier":"🐉 vagas/ação?x=1"}]}""".formatted(profile(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created[0].identifier").value("🐉 vagas/ação?x=1"))
                .andExpect(jsonPath("$.created[0].frequencyMinutes").value(14400));
        assertThat(AtsPaths.segment("a/../b?c#d@e f")).isEqualTo("a%2F..%2Fb%3Fc%23d%40e%20f");
        assertThat(SourceKind.validIdentifier("🐉")).isTrue();
        assertThat(SourceKind.validIdentifier("..")).isFalse();
        assertThat(SourceKind.validIdentifier("a\nb")).isFalse();
        assertThat(SourceKind.validIdentifier("")).isFalse();
    }

    @Test
    void searchUrlKeepsItsFilters() throws Exception {
        String token = newUser();
        String url = "https://www.linkedin.com/jobs/search-results/?currentJobId=4485733519&keywords=java#filtro";
        postJson(token, "/api/v1/sources/bulk", """
                {"profileIds":["%s"],"sources":[{"name":"Busca","kind":"LINKEDIN","url":"%s"}]}""".formatted(profile(token), url))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created[0].url").value(url))
                .andExpect(jsonPath("$.created[0].openUrl").value(url));
    }

    @Test
    void frequencyHasLimits() throws Exception {
        String token = newUser();
        String body = """
                {"profileIds":["%s"],"frequencyMinutes":%d,"sources":[{"name":"A","kind":"GREENHOUSE","identifier":"a"}]}""";
        String id = profile(token);
        postJson(token, "/api/v1/sources/bulk", body.formatted(id, 14)).andExpect(status().isBadRequest());
        postJson(token, "/api/v1/sources/bulk", body.formatted(id, 525_601)).andExpect(status().isBadRequest());
    }

    @Test
    void javascriptUrlIsStillRefused() throws Exception {
        String token = newUser();
        postJson(token, "/api/v1/sources/bulk", """
                {"profileIds":["%s"],"sources":[{"name":"X","kind":"OTHER","url":"javascript:alert(1)"}]}""".formatted(profile(token)))
                .andExpect(status().is4xxClientError());
    }
}
