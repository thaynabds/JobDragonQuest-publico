package dev.jobdragonquest.jobs.link;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;

/** Com o buscador de verdade: link para a rede interna é recusado antes de qualquer conexão. */
class JobLinkBlockedTest extends ApiTestSupport {

    @Test
    void refusesInternalAddressesAndRequiresLogin() throws Exception {
        String token = newUser();
        for (String url : new String[] {"http://127.0.0.1:8080/api/v1/auth/me", "http://169.254.169.254/latest/meta-data/",
                "file:///etc/passwd"}) {
            postJson(token, "/api/v1/jobs/from-link", "{\"url\":\"" + url + "\"}")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("link_blocked"));
        }
        postJson(null, "/api/v1/jobs/from-link", "{\"url\":\"https://example.com\"}").andExpect(status().isUnauthorized());
    }
}
