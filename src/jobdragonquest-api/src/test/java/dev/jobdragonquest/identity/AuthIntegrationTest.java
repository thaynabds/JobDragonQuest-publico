package dev.jobdragonquest.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;

import dev.jobdragonquest.ApiTestSupport;
import dev.jobdragonquest.audit.AuditRepository;
import jakarta.servlet.http.Cookie;

class AuthIntegrationTest extends ApiTestSupport {

    @Autowired
    private AuditRepository audit;

    private MvcResult register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"%s","displayName":"Thayná","locale":"pt-BR","timeZone":"America/Recife"}"""
                        .formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}""".formatted(email, password))).andReturn();
    }

    private static String email() {
        return "a" + UUID.randomUUID() + "@teste.dev";
    }

    @Test
    void registerSetsSecureRefreshCookieAndReturnsShortLivedToken() throws Exception {
        MvcResult result = register(email());

        Cookie refresh = result.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        assertThat(refresh).isNotNull();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth");
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Strict");
        assertThat(result.getResponse().getContentAsString()).contains("\"expiresIn\":900").doesNotContain(refresh.getValue());
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    }

    @Test
    void meReturnsOwnAccountWithoutPasswordHash() throws Exception {
        String email = email();
        String token = com.jayway.jsonpath.JsonPath.read(register(email).getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.timeZone").value("America/Recife"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String email = email();
        register(email);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"%s","displayName":"Outra"}""".formatted(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_taken"));
    }

    @Test
    void weakPasswordAndInvalidTimeZoneAreRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"curta","displayName":"X"}""".formatted(email())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"%s","displayName":"X","timeZone":"Marte/Base"}""".formatted(email(), PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_time_zone"));
    }

    @Test
    void acceptsAnyValidLanguageCodeAndRejectsTheRest() throws Exception {
        for (String locale : new String[] {"es", "fr", "pt-PT", "zh-CN"}) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                            {"email":"%s","password":"%s","displayName":"X","locale":"%s"}""".formatted(email(), PASSWORD, locale)))
                    .andExpect(status().isCreated());
        }
        for (String locale : new String[] {"../etc", "ES", "es_ES", "<script>", "english"}) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                            {"email":"%s","password":"%s","displayName":"X","locale":"%s"}""".formatted(email(), PASSWORD, locale)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void loginErrorsDoNotRevealWhetherAccountExists() throws Exception {
        String email = email();
        register(email);

        MvcResult wrongPassword = login(email, "senha-errada-123456");
        MvcResult unknownUser = login(email(), "senha-errada-123456");

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownUser.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getResponse().getContentAsString()).isEqualTo(unknownUser.getResponse().getContentAsString());
    }

    @Test
    void accountLocksAfterRepeatedFailuresEvenWithCorrectPassword() throws Exception {
        String email = email();
        register(email);
        for (int i = 0; i < 4; i++) {
            assertThat(login(email, "senha-errada-123456").getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(login(email, "senha-errada-123456").getResponse().getStatus()).as("quinto erro bloqueia").isEqualTo(429);
        assertThat(login(email, PASSWORD).getResponse().getStatus()).as("bloqueada").isEqualTo(429);
        assertThat(audit.findAll()).anyMatch(entry -> entry.getAction().equals("auth.login_locked"));
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesWholeFamily() throws Exception {
        Cookie first = register(email()).getResponse().getCookie(AuthController.REFRESH_COOKIE);

        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = rotated.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Reapresentar o token antigo indica roubo: recusa e revoga também o token novo.
        mvc.perform(post("/api/v1/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
        assertThat(audit.findAll()).anyMatch(entry -> entry.getAction().equals("auth.refresh_reuse_detected"));
    }

    @Test
    void refreshFromForeignOriginIsRejected() throws Exception {
        Cookie refresh = register(email()).getResponse().getCookie(AuthController.REFRESH_COOKIE);
        // O filtro CORS recusa antes do controller. A conferência de Origin no controller é a segunda camada.
        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh).header(HttpHeaders.ORIGIN, "https://site-malicioso.example"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh).header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk());
    }

    @Test
    void refreshWithoutOrWithUnknownCookieFails() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(new MockCookie(AuthController.REFRESH_COOKIE, "inventado")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
        Cookie refresh = register(email()).getResponse().getCookie(AuthController.REFRESH_COOKIE);
        mvc.perform(post("/api/v1/auth/logout").cookie(refresh))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthController.REFRESH_COOKIE, 0));
        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRequireValidToken() throws Exception {
        mvc.perform(get("/api/v1/profiles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, "Bearer token.falso.qualquer"))
                .andExpect(status().isUnauthorized());
        // Token assinado com outro segredo.
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDAiLCJpc3MiOiJqb2JmbHkiLCJhdWQiOlsiam9iZmx5LWFwaSJdLCJleHAiOjQxMDI0NDQ4MDB9.Zm9yamFkbw";
        mvc.perform(get("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublicButMetricsAreNot() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/qualquer-coisa")).andExpect(status().isUnauthorized());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")));
    }
}
