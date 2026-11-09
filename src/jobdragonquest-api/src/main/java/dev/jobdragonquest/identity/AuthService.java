package dev.jobdragonquest.identity;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.List;
import java.util.HexFormat;
import java.util.ArrayList;
import java.security.SecureRandom;
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
import dev.jobdragonquest.identity.AuthDtos.ChangeEmailRequest;
import dev.jobdragonquest.identity.AuthDtos.ChangePasswordRequest;
import dev.jobdragonquest.identity.AuthDtos.LoginRequest;
import dev.jobdragonquest.identity.AuthDtos.RecoverRequest;
import dev.jobdragonquest.identity.AuthDtos.UpdateNameRequest;
import dev.jobdragonquest.identity.AuthDtos.RegisterRequest;
import dev.jobdragonquest.identity.AuthDtos.Session;

@Service
public class AuthService {

    private static final int RECOVERY_CODES = 8;
    private static final int MAX_TRACKED_UNKNOWN = 10_000;

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
    private final RecoveryCodeRepository recoveryCodes;
    private final SecureRandom random = new SecureRandom();
    private final java.util.Map<String, UnknownAttempts> unknownAttempts = new java.util.concurrent.ConcurrentHashMap<>();

    AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens, TokenService tokens,
            PasswordEncoder passwordEncoder, AuditService audit, SecurityProperties properties, Clock clock,
            RegistrationProperties registration, RecoveryCodeRepository recoveryCodes) {
        this.registration = registration;
        this.recoveryCodes = recoveryCodes;
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
        int limit = properties.login().lockoutAfterFailures();
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw unknownEmailFailure(normalizeEmail(request.email()), now, limit);
        }
        UserAccount user = found.get();
        if (user.isLocked(now)) {
            audit.recordIndependently(user.getId(), "auth.login_locked", null);
            throw locked(user.getLockedUntil(), now);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.registerFailedLogin(now, limit, properties.login().lockoutDuration());
            audit.recordIndependently(user.getId(), "auth.login_failed", null);
            if (user.isLocked(now)) {
                throw locked(user.getLockedUntil(), now);
            }
            throw wrongPassword(limit - user.getFailedLogins());
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

    @Transactional
    public UserAccount rename(UUID userId, UpdateNameRequest request) {
        UserAccount user = get(userId);
        user.rename(request.displayName().strip(), clock.instant());
        audit.record(userId, "user.name_changed", "user", userId, null);
        return user;
    }

    /** Muda o e-mail de login. Exige a senha atual e respeita a lista de e-mails permitidos do servidor. */
    @Transactional
    public UserAccount changeEmail(UUID userId, ChangeEmailRequest request) {
        UserAccount user = get(userId);
        requirePassword(user, request.currentPassword());
        String email = normalizeEmail(request.email());
        if (email.equals(user.getEmail())) {
            return user;
        }
        if (!registration.allows(email)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "registration_closed", "Este e-mail não é permitido neste servidor");
        }
        if (users.existsByEmail(email)) {
            throw DomainException.conflict("email_taken", "Já existe uma conta com este e-mail");
        }
        user.changeEmail(email, clock.instant());
        audit.record(userId, "user.email_changed", "user", userId, null);
        return user;
    }

    /** Troca a senha e encerra as outras sessões: abre uma sessão nova só para quem fez a troca. */
    @Transactional
    public Session changePassword(UUID userId, ChangePasswordRequest request) {
        UserAccount user = get(userId);
        requirePassword(user, request.currentPassword());
        Instant now = clock.instant();
        user.changePassword(passwordEncoder.encode(request.newPassword()), now);
        refreshTokens.revokeAllOfUser(userId, now);
        audit.record(userId, "user.password_changed", "user", userId, null);
        return openSession(user, UUID.randomUUID(), now);
    }

    /** Gera 8 códigos novos e invalida os anteriores. Os códigos só aparecem nesta resposta. */
    @Transactional
    public List<String> newRecoveryCodes(UUID userId, String currentPassword) {
        UserAccount user = get(userId);
        requirePassword(user, currentPassword);
        recoveryCodes.deleteAllOfUser(userId);
        Instant now = clock.instant();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            String code = HexFormat.of().formatHex(bytes);
            recoveryCodes.save(new RecoveryCode(userId, TokenService.hash(code), now));
            codes.add(code);
        }
        audit.record(userId, "user.recovery_codes_created", "user", userId, null);
        return codes;
    }

    @Transactional(readOnly = true)
    public long recoveryCodesLeft(UUID userId) {
        return recoveryCodes.countByUserIdAndUsedAtIsNull(userId);
    }

    /** Troca a senha com um código de recuperação. Qualquer erro dá a mesma resposta: não revela se o e-mail existe. */
    @Transactional
    public void recover(RecoverRequest request) {
        String code = request.code().strip().toLowerCase(Locale.ROOT).replace("-", "");
        UserAccount user = users.findByEmail(normalizeEmail(request.email())).orElse(null);
        RecoveryCode stored = recoveryCodes.findByCodeHash(TokenService.hash(code)).orElse(null);
        if (user == null || stored == null || stored.isUsed() || !stored.getUserId().equals(user.getId())) {
            throw DomainException.badRequest("invalid_recovery", "E-mail ou código de recuperação inválido");
        }
        Instant now = clock.instant();
        stored.use(now);
        user.changePassword(passwordEncoder.encode(request.newPassword()), now);
        user.registerSuccessfulLogin(now);
        refreshTokens.revokeAllOfUser(user.getId(), now);
        audit.record(user.getId(), "user.recovered", "user", user.getId(), null);
    }

    private void requirePassword(UserAccount user, String password) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // 400, não 401: o painel trata 401 como sessão vencida e tentaria renovar.
            throw DomainException.badRequest("wrong_password", "Senha atual incorreta");
        }
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

    /** E-mail sem conta segue a mesma contagem: a resposta não pode revelar quem está cadastrado. */
    private DomainException unknownEmailFailure(String email, Instant now, int limit) {
        if (unknownAttempts.size() >= MAX_TRACKED_UNKNOWN) {
            unknownAttempts.clear();
        }
        UnknownAttempts current = unknownAttempts.get(email);
        if (current != null && current.lockedUntil() != null && current.lockedUntil().isAfter(now)) {
            return locked(current.lockedUntil(), now);
        }
        int failures = (current == null || current.lockedUntil() != null ? 0 : current.failures()) + 1;
        if (failures >= limit) {
            Instant until = now.plus(properties.login().lockoutDuration());
            unknownAttempts.put(email, new UnknownAttempts(0, until));
            return locked(until, now);
        }
        unknownAttempts.put(email, new UnknownAttempts(failures, null));
        return wrongPassword(limit - failures);
    }

    private record UnknownAttempts(int failures, Instant lockedUntil) {
    }

    private static DomainException wrongPassword(int attemptsLeft) {
        return new LoginFailedException(HttpStatus.UNAUTHORIZED, "invalid_credentials", INVALID_CREDENTIALS, attemptsLeft, null);
    }

    private static DomainException locked(Instant until, Instant now) {
        long minutes = Math.max(1, (java.time.Duration.between(now, until).getSeconds() + 59) / 60);
        return new LoginFailedException(HttpStatus.TOO_MANY_REQUESTS, "account_locked", "Conta bloqueada temporariamente", 0, minutes);
    }

    private static DomainException invalidCredentials() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "invalid_credentials", INVALID_CREDENTIALS);
    }

    private static DomainException invalidSession() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "invalid_session", "Sessão inválida ou expirada");
    }
}
