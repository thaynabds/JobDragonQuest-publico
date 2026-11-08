# 02. Arquitetura

## 1. Visão geral

```
                    ┌──────────────────────────────┐
  Navegador ───────▶│  jobdragonquest-web (React + Vite)   │   pt-BR / en
  Extensão Chrome ─▶│  SPA, consome a API REST     │
                    └──────────────┬───────────────┘
                                   │ HTTPS + JWT (Bearer)
                    ┌──────────────▼───────────────┐
  Cliente MCP ─────▶│  jobdragonquest-api (Spring Boot 4)  │──▶ PostgreSQL
  (Claude Desktop)  │  monólito modular            │──▶ (Redis, fase 2)
   via jobdragonquest-mcp   │  identity · profiles · jobs  │
                    │  matching · applications     │──▶ Provedores LLM (fase 2)
                    │  pending · audit · tasks     │──▶ Gmail / Graph (fase 2)
                    └──────────────────────────────┘──▶ ATS públicos (fase 2)
```

## 2. Decisões (ADRs)

Cada decisão tem um arquivo próprio em [adr/](adr/README.md): monólito modular (001), Java 21 e Spring Boot 4.1 (002), React e TypeScript (003), agendador com reserva no banco (004), compatibilidade determinística (005), autenticação própria (006), MCP separado (007), automação só onde é permitida (008), espelho público (009) e formato único de erro (010).

## 3. Módulos do backend

| Módulo | Responsabilidade | Principais classes |
|---|---|---|
| `identity` | Usuários, senhas, JWT, refresh tokens, papéis | `AuthController`, `AuthService`, `TokenService` |
| `profiles` | Perfis profissionais e seus itens | `ProfileController`, `ProfileService` |
| `jobs` | Vagas, deduplicação, URL canônica | `JobController`, `JobService`, `UrlCanonicalizer` |
| `matching` | Nota de compatibilidade e escolha de perfil | `MatchingEngine`, `SkillNormalizer` |
| `applications` | Candidaturas de qualquer origem, máquina de estados, eventos e evidências | `ApplicationController`, `ApplicationService`, `ApplicationStateMachine` |
| `pending` | Central de pendências (calculada) | `PendingService` |
| `audit` | Registro imutável de operações | `AuditService` |
| `common` | Erros (RFC 9457 Problem Details), segurança, rate limit, config | `GlobalExceptionHandler`, `SecurityConfig` |

## 4. Contratos

A API REST é versionada em `/api/v1`. Os erros seguem o formato Problem Details (RFC 9457). O OpenAPI é gerado em `/v3/api-docs`, e a referência legível está em [api.md](api.md).

## 5. Observabilidade

- Actuator `health` (público, sem detalhes) e `prometheus` (só com papel `ADMIN`).
- Cada linha de log traz o `requestId` (o mesmo `X-Request-Id` da resposta), nunca token, senha ou corpo de e-mail. Log em JSON com `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`.

## 6. Execução

| Ambiente | Banco | Como |
|---|---|---|
| Testes | H2 em memória, modo PostgreSQL | `./mvnw verify` |
| Local | PostgreSQL 127.0.0.1:5432 | variáveis `JOBDRAGONQUEST_DB_*` e `./mvnw spring-boot:run` |
| Docker | `compose.yaml` (Postgres + API + Web) | `docker compose up` (Docker não está instalado nesta máquina) |
