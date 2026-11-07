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

## 2. Decisões (ADRs resumidos)

### ADR-001: monólito modular em vez de microsserviços no início
- **Contexto:** há um usuário principal, uma pessoa desenvolvendo e um computador sem Docker. O prompt pede para evitar tanto um monólito desorganizado quanto uma fragmentação desnecessária.
- **Decisão:** um serviço Spring Boot com módulos isolados por pacote (`identity`, `profiles`, `jobs`, `matching`, `applications`, `pending`, `audit`). Cada módulo expõe só uma fachada (`*Service`) e DTOs. Nenhum módulo acessa o repositório de outro.
- **Consequência:** deploy e transação simples. Um módulo pode virar serviço próprio quando houver carga ou equipe que justifique. Os primeiros candidatos são `collectors` (coleta de vagas) e `mail` (e-mail), por terem I/O externo e ritmo próprio.

### ADR-002: Java 21 + Spring Boot 4.1.2
- Preferência declarada pela usuária, ecossistema maduro de segurança (Spring Security 7, OAuth2 Resource Server), Hibernate 7 e virtual threads para I/O externo.
- A ideia inicial era a 3.5, mas o Spring Initializr não a oferece mais: ela saiu do suporte aberto. Começar um projeto novo numa linha sem correções de segurança seria dívida desde o primeiro dia. Por isso usamos a 4.1.2, a versão estável atual.

### ADR-003: React + TypeScript + Vite no front
- Ecossistema amplo e componentes acessíveis, com i18n por `react-i18next`.
- Angular também atenderia. A escolha pesa a produtividade e a facilidade de manutenção individual.

### ADR-004: fila em banco (padrão outbox) antes de RabbitMQ ou Kafka
- O volume esperado é de dezenas a centenas de tarefas por dia. Uma tabela `task` com `SELECT ... FOR UPDATE SKIP LOCKED` dá fila durável, idempotente e transacional sem infraestrutura extra.
- Kafka só se justifica com vários consumidores independentes e alto volume, o que não é o caso. RabbitMQ entra se a coleta virar um serviço separado.

### ADR-005: compatibilidade determinística e explicável, com IA como camada opcional
- A nota é calculada por regras auditáveis (requisitos obrigatórios, desejáveis, impedimentos eliminatórios), sempre com justificativa.
- O LLM entra para extrair requisitos de texto livre e mapear sinônimos. Ele não decide a nota final sozinho, o que atende RF-04 e RN-02 e funciona sem provedor de IA (RF-08.1).

### ADR-006: autenticação própria com JWT assinado (HS256) e refresh token opaco
- Access token de 15 minutos, validado pelo `oauth2-resource-server`. Refresh token aleatório de 256 bits, guardado só como hash SHA-256, rotativo e com detecção de reuso (um token reutilizado revoga a família).
- O login OIDC (Google, Microsoft) usa o mesmo emissor interno depois da federação (fase 2).

### ADR-007: servidor MCP como processo separado e fino
- `jobdragonquest-mcp` fala stdio com o cliente (Claude Desktop/Code) e chama a API REST com um token de escopo restrito (`mcp:read`, `mcp:write`). Assim, o MCP não tem acesso direto ao banco e herda toda a autorização da API.

### ADR-008: navegação automatizada só onde é permitida
- LinkedIn, Indeed e Glassdoor proíbem scraping e automação nos termos de uso. Para eles, as entradas são link, extensão de navegador (ação iniciada pela usuária), e-mail de alerta e importação.
- Automação de formulários só em ATS que permitam e sempre com pausa em CAPTCHA e MFA. Detalhes em [integracoes.md](integracoes.md).

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
- Logs com `traceId` e `userId`, nunca com token, senha ou corpo de e-mail.

## 6. Execução

| Ambiente | Banco | Como |
|---|---|---|
| Testes | H2 em memória, modo PostgreSQL | `./mvnw verify` |
| Local | PostgreSQL 127.0.0.1:5432 | variáveis `JOBDRAGONQUEST_DB_*` e `./mvnw spring-boot:run` |
| Docker | `compose.yaml` (Postgres + API + Web) | `docker compose up` (Docker não está instalado nesta máquina) |
