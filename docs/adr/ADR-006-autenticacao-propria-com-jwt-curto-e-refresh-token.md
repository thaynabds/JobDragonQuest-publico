# ADR-006: Autenticação própria com JWT curto e refresh token opaco

- **Situação:** Aceita
- **Desde:** v0.1.0

## Contexto

Aplicação local com uma ou poucas contas, sem provedor de identidade obrigatório.

## Decisão

Senha com Argon2id; access token JWT HS256 de 15 minutos validado pelo Resource Server; refresh token aleatório de 256 bits guardado só como hash, rotativo, em cookie `HttpOnly` e `SameSite=Strict`, com detecção de reuso que revoga a família.

## Alternativas consideradas

Sessão em servidor (estado a mais); OIDC obrigatório (dependência externa para uso local).

## Consequências

Login sem serviço externo; token roubado vale pouco tempo. OIDC (Google, Microsoft) pode entrar depois usando o mesmo emissor interno.
