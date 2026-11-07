package dev.jobdragonquest.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;

/** CORS da extensão do navegador (issue #116): sem cookies e só nas rotas da extensão. */
class ExtensionCorsTest extends ApiTestSupport {

    private static final String EXTENSION = "chrome-extension://abcdefghijklmnopabcdefghijklmnop";

    @Test
    void extensionMayPostToItsRoutesWithoutCredentials() throws Exception {
        for (String path : new String[] {"/api/v1/jobs/from-link", "/api/v1/jobs"}) {
            mvc.perform(options(path).header("Origin", EXTENSION).header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "authorization,content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", EXTENSION))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        }
        mvc.perform(options("/api/v1/jobs/from-link").header("Origin", "moz-extension://1234-5678")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());
    }

    @Test
    void extensionCannotReachOtherRoutesOrMethods() throws Exception {
        mvc.perform(options("/api/v1/applications").header("Origin", EXTENSION).header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/auth/refresh").header("Origin", EXTENSION).header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/jobs").header("Origin", EXTENSION).header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void panelKeepsCredentialsAndUnknownSitesAreRefused() throws Exception {
        mvc.perform(options("/api/v1/applications").header("Origin", "http://127.0.0.1:5173").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/v1/jobs").header("Origin", "https://site-malicioso.test").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
