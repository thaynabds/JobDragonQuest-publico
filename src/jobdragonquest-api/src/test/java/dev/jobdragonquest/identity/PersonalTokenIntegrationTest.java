package dev.jobdragonquest.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;

import dev.jobdragonquest.ApiTestSupport;

/** Critérios de aceite da issue #57: tokens de acesso pessoal para integrações. */
class PersonalTokenIntegrationTest extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private String[] create(String session, String name) throws Exception {
        String body = postJson(session, "/api/v1/tokens", """
                {"name":"%s","days":30}""".formatted(name)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new String[] {JsonPath.read(body, "$.token.id"), JsonPath.read(body, "$.value")};
    }

    @Test
    void tokenIsShownOnceStoredHashedAndWorksForTheApi() throws Exception {
        String session = newUser();
        String[] token = create(session, "MCP do Claude");
        assertThat(token[1]).startsWith("jdq_").hasSize(47);

        getJson(session, "/api/v1/tokens")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("MCP do Claude"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].value").doesNotExist())
                .andExpect(jsonPath("$[0].prefix").value(token[1].substring(0, 10) + "…"));
        List<Map<String, Object>> rows = jdbc.queryForList("select * from personal_token where id = ?",
                java.util.UUID.fromString(token[0]));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().values()).as("o valor do token nunca é gravado").noneMatch(value -> token[1].equals(value));

        postJson(session, "/api/v1/profiles", """
                {"name":"Java","seniority":"JUNIOR"}""").andExpect(status().isCreated());
        getJson(token[1], "/api/v1/profiles").andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Java"));
        getJson(token[1], "/api/v1/me").andExpect(status().isOk());
    }

    @Test
    void tokenCannotManageTokens() throws Exception {
        String session = newUser();
        String[] token = create(session, "Congo");
        postJson(token[1], "/api/v1/tokens", "{\"name\":\"outro\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("session_required"));
        getJson(token[1], "/api/v1/tokens").andExpect(status().isForbidden());
        deleteJson(token[1], "/api/v1/tokens/" + token[0]).andExpect(status().isForbidden());
    }

    @Test
    void revokedExpiredAndForgedTokensGet401() throws Exception {
        String session = newUser();
        String[] revoked = create(session, "Revogado");
        String[] expired = create(session, "Vencido");
        deleteJson(session, "/api/v1/tokens/" + revoked[0]).andExpect(status().isNoContent());
        getJson(revoked[1], "/api/v1/profiles").andExpect(status().isUnauthorized());

        jdbc.update("update personal_token set expires_at = ? where id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), java.util.UUID.fromString(expired[0]));
        getJson(expired[1], "/api/v1/profiles").andExpect(status().isUnauthorized());

        getJson("jdq_" + "A".repeat(43), "/api/v1/profiles").andExpect(status().isUnauthorized());
        getJson("jdq_curto", "/api/v1/profiles").andExpect(status().isUnauthorized());
        getJson(session, "/api/v1/tokens").andExpect(jsonPath("$[?(@.name=='Revogado')].active").value(hasItem(false)));
    }

    @Test
    void validatesNameDaysAndLimit() throws Exception {
        String session = newUser();
        postJson(session, "/api/v1/tokens", "{\"name\":\"\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_token_name"));
        postJson(session, "/api/v1/tokens", "{\"name\":\"<script>\"}").andExpect(status().isBadRequest());
        postJson(session, "/api/v1/tokens", "{\"name\":\"ok\",\"days\":0}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_token_days"));
        postJson(session, "/api/v1/tokens", "{\"name\":\"ok\",\"days\":366}").andExpect(status().isBadRequest());
        for (int i = 0; i < 10; i++) {
            create(session, "token " + i);
        }
        postJson(session, "/api/v1/tokens", "{\"name\":\"onze\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("too_many_tokens"));
    }

    @Test
    void tokensAreIsolatedBetweenAccounts() throws Exception {
        String owner = newUser();
        String intruder = newUser();
        String[] token = create(owner, "Meu");
        deleteJson(intruder, "/api/v1/tokens/" + token[0]).andExpect(status().isNotFound());
        getJson(intruder, "/api/v1/tokens").andExpect(jsonPath("$[*].id").value(not(hasItem(token[0]))));
        postJson(owner, "/api/v1/profiles", "{\"name\":\"Privado\",\"seniority\":\"JUNIOR\"}").andExpect(status().isCreated());
        getJson(intruder, "/api/v1/profiles").andExpect(jsonPath("$[*].name").value(not(hasItem("Privado"))));
        getJson(token[1], "/api/v1/tokens").andExpect(status().isForbidden());
    }
}
