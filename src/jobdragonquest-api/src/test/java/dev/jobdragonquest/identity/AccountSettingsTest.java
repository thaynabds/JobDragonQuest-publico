package dev.jobdragonquest.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import dev.jobdragonquest.ApiTestSupport;

/** Issue #172: painel da conta (nome, e-mail, senha) e tokens sem validade. */
class AccountSettingsTest extends ApiTestSupport {

    private String register(String email) throws Exception {
        return read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","displayName":"Pessoa"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated()), "$.accessToken");
    }

    private int login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(email, password))).andReturn().getResponse().getStatus();
    }

    private static String email() {
        return "c" + UUID.randomUUID() + "@teste.dev";
    }

    @Test
    void changesNameAndEmailOnlyWithTheCurrentPassword() throws Exception {
        String email = email();
        String token = register(email);
        putJson(token, "/api/v1/me", "{\"displayName\":\"Nome Novo\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Nome Novo"));

        String other = email();
        putJson(token, "/api/v1/me/email", "{\"email\":\"%s\",\"currentPassword\":\"errada\"}".formatted(other))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("wrong_password"));
        putJson(token, "/api/v1/me/email", "{\"email\":\"%s\",\"currentPassword\":\"%s\"}".formatted(other, PASSWORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(other));
        assertThat(login(other, PASSWORD)).isEqualTo(200);
        assertThat(login(email, PASSWORD)).isEqualTo(401);
    }

    @Test
    void emailOfAnotherAccountIsRefused() throws Exception {
        String taken = email();
        register(taken);
        putJson(register(email()), "/api/v1/me/email", "{\"email\":\"%s\",\"currentPassword\":\"%s\"}".formatted(taken, PASSWORD))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("email_taken"));
    }

    @Test
    void changesPasswordAndRefusesWeakOrWrongOnes() throws Exception {
        String email = email();
        String token = register(email);
        putJson(token, "/api/v1/me/password", "{\"currentPassword\":\"errada\",\"newPassword\":\"nova-senha-bem-forte\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("wrong_password"));
        putJson(token, "/api/v1/me/password", "{\"currentPassword\":\"%s\",\"newPassword\":\"curta\"}".formatted(PASSWORD))
                .andExpect(status().isBadRequest());
        putJson(token, "/api/v1/me/password", "{\"currentPassword\":\"%s\",\"newPassword\":\"nova-senha-bem-forte\"}".formatted(PASSWORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        assertThat(login(email, "nova-senha-bem-forte")).isEqualTo(200);
        assertThat(login(email, PASSWORD)).isEqualTo(401);
    }

    @Test
    void tokenWithoutExpiryWorksAndCannotChangeTheAccount() throws Exception {
        String session = newUser();
        var created = postJson(session, "/api/v1/tokens", "{\"name\":\"sempre\",\"neverExpires\":true}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.token.expiresAt").doesNotExist());
        String value = read(created, "$.value");
        getJson(value, "/api/v1/profiles").andExpect(status().isOk());
        getJson(session, "/api/v1/tokens").andExpect(jsonPath("$[0].active").value(true));
        putJson(value, "/api/v1/me", "{\"displayName\":\"Invasor\"}").andExpect(status().isForbidden());
        putJson(value, "/api/v1/me/password", "{\"currentPassword\":\"%s\",\"newPassword\":\"nova-senha-bem-forte\"}".formatted(PASSWORD))
                .andExpect(status().isForbidden());
    }

    private String codes(String token) throws Exception {
        return putless(token);
    }

    private String putless(String token) throws Exception {
        return mvc.perform(auth(post("/api/v1/me/recovery-codes"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\"}".formatted(PASSWORD))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions recover(String email, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/recover").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","code":"%s","newPassword":"senha-recuperada-forte"}""".formatted(email, code)));
    }

    @Test
    void recoveryCodeChangesThePasswordOnlyOnceAndOnlyForItsOwner() throws Exception {
        String email = email();
        String other = email();
        register(other);
        String body = codes(register(email));
        String code = com.jayway.jsonpath.JsonPath.read(body, "$.codes[0]");
        recover(other, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_recovery"));
        recover("ninguem" + email, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_recovery"));
        recover(email, code).andExpect(status().isNoContent());
        assertThat(login(email, "senha-recuperada-forte")).isEqualTo(200);
        assertThat(login(email, PASSWORD)).isEqualTo(401);
        recover(email, code).andExpect(status().isBadRequest());
    }

    @Test
    void personalTokenCannotCreateRecoveryCodes() throws Exception {
        String session = newUser();
        String value = read(postJson(session, "/api/v1/tokens", "{\"name\":\"t\",\"days\":30}"), "$.value");
        postJson(value, "/api/v1/me/recovery-codes", "{\"currentPassword\":\"%s\"}".formatted(PASSWORD)).andExpect(status().isForbidden());
    }

    @Test
    void withoutRememberMeTheCookieLastsOnlyTheBrowserSession() throws Exception {
        String email = email();
        register(email);
        String cookie = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","rememberMe":false}""".formatted(email, PASSWORD))).andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(cookie).doesNotContain("Max-Age").contains("HttpOnly");
        String kept = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","rememberMe":true}""".formatted(email, PASSWORD))).andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(kept).contains("Max-Age");
    }

    private org.springframework.test.web.servlet.ResultActions attempt(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(email, password)));
    }

    private void countsDownAndLocks(String email) throws Exception {
        for (int left = 4; left >= 1; left--) {
            attempt(email, "senha-errada").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.attemptsLeft").value(left));
        }
        attempt(email, "senha-errada").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("account_locked")).andExpect(jsonPath("$.retryAfterMinutes").value(15));
    }

    @Test
    void tellsAttemptsLeftAndLocksOnlyAfterTheFifthError() throws Exception {
        String email = email();
        register(email);
        countsDownAndLocks(email);
        attempt(email, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void unknownEmailGetsTheSameAnswer() throws Exception {
        countsDownAndLocks(email());
    }

    @Test
    void successResetsTheCount() throws Exception {
        String email = email();
        register(email);
        attempt(email, "senha-errada").andExpect(jsonPath("$.attemptsLeft").value(4));
        attempt(email, PASSWORD).andExpect(status().isOk());
        attempt(email, "senha-errada").andExpect(jsonPath("$.attemptsLeft").value(4));
    }

    /** Abrir muitas páginas em seguida não pode derrubar a sessão. */
    @Test
    void manyRefreshesInARowKeepTheSession() throws Exception {
        String email = email();
        register(email);
        var login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD))).andReturn().getResponse();
        jakarta.servlet.http.Cookie cookie = login.getCookie(AuthController.REFRESH_COOKIE);
        for (int i = 0; i < 15; i++) {
            var refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isOk()).andReturn().getResponse();
            cookie = refreshed.getCookie(AuthController.REFRESH_COOKIE);
        }
    }
}
