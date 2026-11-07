package dev.jobdragonquest.common.security;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração de segurança. O construtor recusa um segredo JWT ausente ou curto, para a aplicação não subir insegura.
 */
@ConfigurationProperties(prefix = "jobdragonquest.security")
public record SecurityProperties(
        String jwtSecret,
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean secureCookies,
        List<String> allowedOrigins,
        Login login) {

    private static final int MIN_SECRET_BYTES = 32;

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("Defina JOBDRAGONQUEST_JWT_SECRET (Base64, 32 bytes ou mais). Ex.: openssl rand -base64 32");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(jwtSecret.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JOBDRAGONQUEST_JWT_SECRET precisa estar em Base64", ex);
        }
        if (decoded.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JOBDRAGONQUEST_JWT_SECRET precisa ter pelo menos 32 bytes");
        }
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    public byte[] jwtSecretBytes() {
        return Base64.getDecoder().decode(jwtSecret.trim());
    }

    public record Login(int maxAttemptsPerWindow, Duration window, int lockoutAfterFailures, Duration lockoutDuration) {
    }
}
