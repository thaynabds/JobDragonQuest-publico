package dev.jobfly.common.security;

import java.util.Collection;
import java.util.UUID;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/** Autenticação por token de acesso pessoal. Não carrega o valor do token, só o dono e o id do token. */
public class PersonalTokenAuthentication extends AbstractAuthenticationToken {

    private final UUID userId;
    private final UUID tokenId;

    public PersonalTokenAuthentication(UUID userId, UUID tokenId, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.userId = userId;
        this.tokenId = tokenId;
        setAuthenticated(true);
    }

    public UUID userId() {
        return userId;
    }

    public UUID tokenId() {
        return tokenId;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return userId.toString();
    }
}
