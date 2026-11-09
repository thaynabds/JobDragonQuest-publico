package dev.jobdragonquest.identity;

import java.util.UUID;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.security.CurrentUser;
import dev.jobdragonquest.common.security.LoginRateLimiter;
import dev.jobdragonquest.common.security.SecurityProperties;
import dev.jobdragonquest.identity.AuthDtos.ChangeEmailRequest;
import dev.jobdragonquest.identity.AuthDtos.ChangePasswordRequest;
import dev.jobdragonquest.identity.AuthDtos.CurrentPasswordRequest;
import dev.jobdragonquest.identity.AuthDtos.LoginRequest;
import dev.jobdragonquest.identity.AuthDtos.RecoverRequest;
import dev.jobdragonquest.identity.AuthDtos.RecoveryCodes;
import dev.jobdragonquest.identity.AuthDtos.RecoveryStatus;
import dev.jobdragonquest.identity.AuthDtos.UpdateNameRequest;
import dev.jobdragonquest.identity.AuthDtos.MeResponse;
import dev.jobdragonquest.identity.AuthDtos.RegisterRequest;
import dev.jobdragonquest.identity.AuthDtos.Session;
import dev.jobdragonquest.identity.AuthDtos.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
class AuthController {

    static final String REFRESH_COOKIE = "jobdragonquest_rt";
    private static final String REMEMBER_COOKIE = "jdq_remember";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService auth;
    private final LoginRateLimiter rateLimiter;
    private final SecurityProperties properties;
    private final CurrentUser currentUser;

    AuthController(AuthService auth, LoginRateLimiter rateLimiter, SecurityProperties properties, CurrentUser currentUser) {
        this.auth = auth;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.currentUser = currentUser;
    }

    @PostMapping("/auth/register")
    ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        limit("register", request, body.email());
        return withSession(HttpStatus.CREATED, auth.register(body));
    }

    @PostMapping("/auth/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        limit("login", request, body.email());
        return withSession(HttpStatus.OK, auth.login(body), !Boolean.FALSE.equals(body.rememberMe()));
    }

    @PostMapping("/auth/refresh")
    ResponseEntity<TokenResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
            @CookieValue(name = REMEMBER_COOKIE, required = false) String remember,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin, HttpServletRequest request) {
        checkOrigin(origin);
        limit("refresh", request, "");
        if (token == null || token.isBlank()) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "invalid_session", "Sessão inválida ou expirada");
        }
        return withSession(HttpStatus.OK, auth.refresh(token), !"0".equals(remember));
    }

    /** "Esqueci minha senha": troca a senha com um código de recuperação gerado antes na conta. */
    @PostMapping("/auth/recover")
    ResponseEntity<Void> recover(@Valid @RequestBody RecoverRequest body, HttpServletRequest request) {
        limit("recover", request, body.email());
        auth.recover(body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/recovery-codes")
    RecoveryStatus recoveryStatus() {
        return new RecoveryStatus(auth.recoveryCodesLeft(currentUser.requireSession()));
    }

    @PostMapping("/me/recovery-codes")
    ResponseEntity<RecoveryCodes> newRecoveryCodes(@Valid @RequestBody CurrentPasswordRequest body, HttpServletRequest http) {
        UUID id = currentUser.requireSession();
        limit("recovery-codes", http, id.toString());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new RecoveryCodes(auth.newRecoveryCodes(id, body.currentPassword())));
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        checkOrigin(origin);
        if (token != null && !token.isBlank()) {
            auth.logout(token);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    @GetMapping("/me")
    MeResponse me() {
        return MeResponse.of(auth.get(currentUser.id()));
    }

    @PutMapping("/me")
    MeResponse rename(@Valid @RequestBody UpdateNameRequest request) {
        return MeResponse.of(auth.rename(currentUser.requireSession(), request));
    }

    @PutMapping("/me/email")
    MeResponse changeEmail(@Valid @RequestBody ChangeEmailRequest request, HttpServletRequest http) {
        UUID id = currentUser.requireSession();
        limit("email", http, id.toString());
        return MeResponse.of(auth.changeEmail(id, request));
    }

    @PutMapping("/me/password")
    ResponseEntity<TokenResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        UUID id = currentUser.requireSession();
        limit("password", http, id.toString());
        return withSession(HttpStatus.OK, auth.changePassword(id, request));
    }

    private ResponseEntity<TokenResponse> withSession(HttpStatus status, Session session) {
        return withSession(status, session, true);
    }

    /** Sem "permanecer conectado", o cookie não tem validade gravada e some quando o navegador fecha. */
    private ResponseEntity<TokenResponse> withSession(HttpStatus status, Session session, boolean remember) {
        Duration age = remember ? properties.refreshTokenTtl() : Duration.ofSeconds(-1);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), age).toString())
                .header(HttpHeaders.SET_COOKIE, cookie(REMEMBER_COOKIE, remember ? "1" : "0", age).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new TokenResponse(session.accessToken(), "Bearer", session.expiresIn()));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return cookie(REFRESH_COOKIE, value, maxAge);
    }

    private ResponseCookie cookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.secureCookies())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    /** Defesa extra contra CSRF nos endpoints que usam o cookie: o navegador sempre envia Origin em POST. */
    private void checkOrigin(String origin) {
        if (origin != null && !properties.allowedOrigins().contains(origin)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "origin_not_allowed", "Origem não permitida");
        }
    }

    private void limit(String action, HttpServletRequest request, String email) {
        String key = action + "|" + request.getRemoteAddr() + "|" + AuthService.normalizeEmail(email);
        if (!rateLimiter.tryAcquire(key)) {
            throw new DomainException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Muitas tentativas. Aguarde alguns minutos.");
        }
    }
}
