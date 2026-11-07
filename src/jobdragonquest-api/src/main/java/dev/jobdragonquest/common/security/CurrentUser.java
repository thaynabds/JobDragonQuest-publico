package dev.jobdragonquest.common.security;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import dev.jobdragonquest.common.error.DomainException;

/**
 * Identidade de quem fez a requisição, lida do JWT validado. Os serviços filtram todos os dados por esse id.
 */
@Component
public class CurrentUser {

    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return UUID.fromString(jwt.getToken().getSubject());
        }
        if (authentication instanceof PersonalTokenAuthentication token) {
            return token.userId();
        }
        throw new AuthenticationCredentialsNotFoundException("Sem usuário autenticado");
    }

    /**
     * Exige sessão de login (JWT do painel). Integrações com token pessoal recebem 403: um token não cria nem revoga
     * outros tokens, então quem o obtiver não consegue se manter dentro da conta depois da revogação.
     */
    public UUID requireSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken) {
            return id();
        }
        throw new DomainException(HttpStatus.FORBIDDEN, "session_required", "Esta ação exige login no painel");
    }
}
