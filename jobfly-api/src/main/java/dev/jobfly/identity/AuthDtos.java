package dev.jobfly.identity;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            // Mínimo de 12 caracteres (NIST SP 800-63B). O máximo evita abuso do custo do Argon2.
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(max = 120) String displayName,
            @Pattern(regexp = "pt-BR|en") String locale,
            @Size(max = 64) String timeZone) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    public record MeResponse(UUID id, String email, String displayName, String role, String locale, String timeZone) {

        static MeResponse of(UserAccount user) {
            return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name(),
                    user.getLocale(), user.getTimeZone());
        }
    }

    /** Resultado interno: o token de acesso vai no corpo, e o refresh token vai só para o cookie HttpOnly. */
    record Session(String accessToken, long expiresIn, String refreshToken) {
    }
}
