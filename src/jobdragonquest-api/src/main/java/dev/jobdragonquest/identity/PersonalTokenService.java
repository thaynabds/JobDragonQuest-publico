package dev.jobdragonquest.identity;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jobdragonquest.audit.AuditService;
import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.error.NotFoundException;
import dev.jobdragonquest.common.security.PersonalTokenAuthentication;

/** Tokens de acesso pessoal: "jdq_" + 32 bytes aleatórios em Base64 URL. O banco guarda só o SHA-256. */
@Service
public class PersonalTokenService {

    public static final String PREFIX = "jdq_";
    static final int MAX_ACTIVE = 10;
    static final int MIN_DAYS = 1;
    static final int MAX_DAYS = 365;
    static final int DEFAULT_DAYS = 90;
    private static final Pattern FORMAT = Pattern.compile("jdq_[A-Za-z0-9_-]{43}");
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} ._()-]{1,80}");
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

    private final PersonalTokenRepository tokens;
    private final UserAccountRepository users;
    private final AuditService audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    PersonalTokenService(PersonalTokenRepository tokens, UserAccountRepository users, AuditService audit, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    public record TokenView(UUID id, String name, String prefix, Instant createdAt, Instant expiresAt, Instant lastUsedAt,
            Instant revokedAt, boolean active) {
    }

    /** Resposta da criação: a única vez em que o valor do token aparece. */
    public record CreatedToken(TokenView token, String value) {
    }

    public record CreateRequest(String name, Integer days) {
    }

    @Transactional
    public CreatedToken create(UUID userId, CreateRequest request) {
        String name = request == null || request.name() == null ? "" : request.name().strip();
        if (!NAME.matcher(name).matches()) {
            throw DomainException.badRequest("invalid_token_name", "Dê um nome de 1 a 80 letras, números, espaço ou . _ ( ) -");
        }
        int days = request.days() == null ? DEFAULT_DAYS : request.days();
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw DomainException.badRequest("invalid_token_days", "Validade de " + MIN_DAYS + " a " + MAX_DAYS + " dias");
        }
        Instant now = clock.instant();
        if (tokens.countActive(userId, now) >= MAX_ACTIVE) {
            throw new DomainException(HttpStatus.CONFLICT, "too_many_tokens", "Revogue um token antes de criar outro (máximo " + MAX_ACTIVE + ")");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PersonalToken token = tokens.save(new PersonalToken(userId, name, TokenService.hash(value),
                value.substring(0, PREFIX.length() + 6), now, now.plus(Duration.ofDays(days))));
        audit.record(userId, "token.created", "personal_token", token.getId(), name);
        return new CreatedToken(view(token, now), value);
    }

    @Transactional(readOnly = true)
    public List<TokenView> list(UUID userId) {
        Instant now = clock.instant();
        return tokens.findByUserIdOrderByCreatedAtDesc(userId).stream().map(token -> view(token, now)).toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID tokenId) {
        PersonalToken token = tokens.findByIdAndUserId(tokenId, userId).orElseThrow(() -> new NotFoundException("Token"));
        token.revoke(clock.instant());
        audit.record(userId, "token.revoked", "personal_token", tokenId, token.getName());
    }

    /**
     * Valida um token apresentado no cabeçalho Authorization. Formato errado, token desconhecido, revogado, vencido ou
     * de conta inexistente: vazio, sem dizer qual foi o motivo.
     */
    @Transactional
    public Optional<PersonalTokenAuthentication> authenticate(String presented) {
        if (presented == null || !FORMAT.matcher(presented).matches()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Optional<PersonalToken> found = tokens.findByTokenHash(TokenService.hash(presented)).filter(token -> token.isActive(now));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PersonalToken token = found.get();
        Optional<UserAccount> user = users.findById(token.getUserId());
        if (user.isEmpty()) {
            return Optional.empty();
        }
        if (token.getLastUsedAt() == null || token.getLastUsedAt().plus(TOUCH_INTERVAL).isBefore(now)) {
            token.touch(now);
        }
        return Optional.of(new PersonalTokenAuthentication(token.getUserId(), token.getId(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.get().getRole().name()))));
    }

    private static TokenView view(PersonalToken token, Instant now) {
        return new TokenView(token.getId(), token.getName(), token.getPrefix() + "…", token.getCreatedAt(),
                token.getExpiresAt(), token.getLastUsedAt(), token.getRevokedAt(), token.isActive(now));
    }
}
