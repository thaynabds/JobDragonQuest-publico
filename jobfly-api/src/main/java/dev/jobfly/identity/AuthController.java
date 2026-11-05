package dev.jobfly.identity;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jobfly.common.error.DomainException;
import dev.jobfly.common.security.CurrentUser;
import dev.jobfly.common.security.LoginRateLimiter;
import dev.jobfly.common.security.SecurityProperties;
import dev.jobfly.identity.AuthDtos.LoginRequest;
import dev.jobfly.identity.AuthDtos.MeResponse;
import dev.jobfly.identity.AuthDtos.RegisterRequest;
import dev.jobfly.identity.AuthDtos.Session;
import dev.jobfly.identity.AuthDtos.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
class AuthController {

    static final String REFRESH_COOKIE = "jobfly_rt";
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
        return withSession(HttpStatus.OK, auth.login(body));
    }

    @PostMapping("/auth/refresh")
    ResponseEntity<TokenResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin, HttpServletRequest request) {
        checkOrigin(origin);
        limit("refresh", request, "");
        if (token == null || token.isBlank()) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "invalid_session", "Sessão inválida ou expirada");
        }
        return withSession(HttpStatus.OK, auth.refresh(token));
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

    private ResponseEntity<TokenResponse> withSession(HttpStatus status, Session session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), properties.refreshTokenTtl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new TokenResponse(session.accessToken(), "Bearer", session.expiresIn()));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
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
