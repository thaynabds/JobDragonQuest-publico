package dev.jobdragonquest.identity;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jobdragonquest.audit.AuditService;
import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.error.NotFoundException;
import dev.jobdragonquest.common.security.SecurityProperties;
import dev.jobdragonquest.identity.AuthDtos.LoginRequest;
import dev.jobdragonquest.identity.AuthDtos.RegisterRequest;
import dev.jobdragonquest.identity.AuthDtos.Session;

@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "E-mail ou senha inválidos, ou conta bloqueada temporariamente";
    private static final String DEFAULT_TIME_ZONE = "America/Recife";

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final SecurityProperties properties;
    private final Clock clock;
    // Hash fixo usado quando o e-mail não existe, para o tempo de resposta não revelar quem tem conta.
    private final String dummyHash;

    private final RegistrationProperties registration;

    AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens, TokenService tokens,
            PasswordEncoder passwordEncoder, AuditService audit, SecurityProperties properties, Clock clock,
            RegistrationProperties registration) {
        this.registration = registration;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public Session register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (!registration.allows(email)) {
            // Mesma resposta para qualquer e-mail fora da lista: não revela quem pode se cadastrar.
            throw new DomainException(HttpStatus.FORBIDDEN, "registration_closed", "Cadastro fechado neste servidor");
        }
        if (users.existsByEmail(email)) {
            throw DomainException.conflict("email_taken", "Já existe uma conta com este e-mail");
        }
        Instant now = clock.instant();
        UserAccount user = users.save(new UserAccount(email, passwordEncoder.encode(request.password()),
                request.displayName().strip(), request.locale() == null ? "pt-BR" : request.locale(),
                validTimeZone(request.timeZone()), now));
        audit.record(user.getId(), "user.registered", "user", user.getId(), null);
        return openSession(user, UUID.randomUUID(), now);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public Session login(LoginRequest request) {
        Instant now = clock.instant();
        Optional<UserAccount> found = users.findByEmail(normalizeEmail(request.email()));
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw invalidCredentials();
        }
        UserAccount user = found.get();
        if (user.isLocked(now)) {
            audit.recordIndependently(user.getId(), "auth.login_locked", null);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.registerFailedLogin(now, properties.login().lockoutAfterFailures(), properties.login().lockoutDuration());
            audit.recordIndependently(user.getId(), "auth.login_failed", null);
            throw invalidCredentials();
        }
        user.registerSuccessfulLogin(now);
        audit.record(user.getId(), "auth.login", "user", user.getId(), null);
        return openSession(user, UUID.randomUUID(), now);
    }

    /** Troca o refresh token por um novo par. Um token já usado revoga a família inteira (detecção de roubo). */
    @Transactional(noRollbackFor = DomainException.class)
    public Session refresh(String presentedToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(TokenService.hash(presentedToken))
                .orElseThrow(AuthService::invalidSession);
        if (current.isRevoked()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            audit.recordIndependently(current.getUserId(), "auth.refresh_reuse_detected", "família revogada");
            throw invalidSession();
        }
        if (!current.isUsable(now)) {
            throw invalidSession();
        }
        current.revoke(now);
        UserAccount user = users.findById(current.getUserId()).orElseThrow(AuthService::invalidSession);
        return openSession(user, current.getFamilyId(), now);
    }

    @Transactional
    public void logout(String presentedToken) {
        refreshTokens.findByTokenHash(TokenService.hash(presentedToken))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public UserAccount get(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Usuário"));
    }

    private Session openSession(UserAccount user, UUID familyId, Instant now) {
        String refresh = tokens.newRefreshToken();
        refreshTokens.save(new RefreshToken(user.getId(), familyId, TokenService.hash(refresh),
                now.plus(properties.refreshTokenTtl()), now));
        return new Session(tokens.accessToken(user), tokens.accessTokenTtlSeconds(), refresh);
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static String validTimeZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return DEFAULT_TIME_ZONE;
        }
        try {
            return ZoneId.of(zone).getId();
        } catch (DateTimeException ex) {
            throw DomainException.badRequest("invalid_time_zone", "Fuso horário inválido");
        }
    }

    private static DomainException invalidCredentials() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "invalid_credentials", INVALID_CREDENTIALS);
    }

    private static DomainException invalidSession() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "invalid_session", "Sessão inválida ou expirada");
    }
}
