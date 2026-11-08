package dev.jobdragonquest.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import dev.jobdragonquest.ApiTestSupport;

/** Issue #159: toda resposta traz o X-Request-Id que identifica a requisição no log. */
class RequestIdFilterTest extends ApiTestSupport {

    @Test
    void generatesAnIdWhenTheClientSendsNone() throws Exception {
        String id = mvc.perform(get("/actuator/health")).andReturn().getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(id).matches("[0-9a-f-]{36}");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void keepsAValidIdFromTheClient() throws Exception {
        mvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, "abc-12345678"))
                .andExpect(header().string(RequestIdFilter.HEADER, "abc-12345678"));
    }

    @Test
    void replacesAnIdThatCouldInjectLogLines() {
        assertThat(RequestIdFilter.resolve("abc\nFALSO LOG")).doesNotContain("\n").matches("[0-9a-f-]{36}");
        assertThat(RequestIdFilter.resolve("x".repeat(65))).hasSize(36);
        assertThat(RequestIdFilter.resolve(null)).hasSize(36);
    }
}
