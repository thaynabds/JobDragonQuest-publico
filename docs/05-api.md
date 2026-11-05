# 05. API REST v1

- **Base:** `/api/v1`.
- **Autenticação:** `Authorization: Bearer <accessToken>`, menos em `auth/*`.
- **Corpo:** JSON de até 1 MiB.
- **Erros:** formato Problem Details (RFC 9457), com o campo extra `code`, estável para o cliente traduzir.

```json
{"status":409,"title":"Conflict","detail":"O perfil mudou desde que foi aberto...","code":"stale_version","instance":"/api/v1/profiles/…"}
```

## Autenticação

| Método e rota | Corpo | Resposta |
|---|---|---|
| `POST /auth/register` | `email`, `password` (12–128), `displayName`, `locale` (`pt-BR` ou `en`), `timeZone` | 201 `{accessToken, tokenType, expiresIn}` + cookie `jobfly_rt` |
| `POST /auth/login` | `email`, `password` | 200, igual ao register. 401 genérico. 429 quando o limite estoura |
| `POST /auth/refresh` | cookie `jobfly_rt` | 200 com novo par. Reusar o token antigo revoga a família inteira |
| `POST /auth/logout` | cookie | 204, revoga e limpa o cookie |
| `GET /me` | — | Dados da conta (sem hash) |

O cookie `jobfly_rt` é `HttpOnly`, `SameSite=Strict`, `Secure` (configurável) e tem `Path=/api/v1/auth`. O token de acesso dura 15 minutos e deve ficar só em memória no front.

## Perfis

| Rota | Descrição |
|---|---|
| `GET /profiles` | Resumo de cada perfil |
| `POST /profiles` | Cria o perfil. 409 se o nome repetir |
| `GET /profiles/{id}` | Perfil completo |
| `PUT /profiles/{id}` | Substitui o perfil. Exige `version` (lock otimista, 409 `stale_version`) |
| `DELETE /profiles/{id}` | 204 |

Campos: `name`, `objective`, `seniority` (INTERN, JUNIOR, MID, SENIOR, LEAD), `minMatchScore` (0–100), `salaryExpectation` + `salaryCurrency`, `availability`, `restrictions`, `preferredLanguage`, `desiredTitles[]`, `locations[]`, `workModes[]` (REMOTE, HYBRID, ONSITE), `skills[] {name, level 1–5, years}`, `languages[] {code ISO 639-1, level A1…C2 | NATIVE}`, `entries[] {kind, title, organization, startDate, endDate, description, url}`.

## Vagas e compatibilidade

| Rota | Descrição |
|---|---|
| `GET /jobs?page&size` | Paginado (`size` vai até 100) |
| `POST /jobs` | 201 se a vaga é nova. 200 + `X-Duplicate-Of` se ela já existia |
| `GET/PUT /jobs/{id}` | Ler e atualizar |
| `PATCH /jobs/{id}/status` | `{status: OPEN \| CLOSED \| UNKNOWN}` |
| `GET /jobs/{id}/matches` | Todos os perfis, do mais para o menos adequado |
| `GET /jobs/{id}/matches/{profileId}` | Análise de um perfil |

Resultado da análise: `score` (0–100 ou null), `recommendation` (APPLY, REVIEW, SKIP, INSUFFICIENT_DATA), `requirements[] {kind, text, skill, outcome MET|PARTIAL|UNMET|NOT_EVALUATED, reason}`, `blockers[] {code WORK_MODE|LOCATION|LANGUAGE|SENIORITY, message}`, `strengths[]`, `warnings[]`, `titleAffinity`, `summary`.

## Candidaturas

| Rota | Descrição |
|---|---|
| `GET /applications` | Filtros `state`, `profileId`, `platform`, `company`, `country`, `appliedFrom`, `appliedTo` (ISO-8601), `staleOnly`, `page`, `size` |
| `GET /applications/board` | Kanban: 19 colunas, com contagem e até 50 cartões por coluna |
| `POST /applications` | Cabeçalho opcional `Idempotency-Key` (8–100 caracteres `[A-Za-z0-9_-]`). 201 se nova. 200 + `X-Duplicate-Of` se for duplicata. 200 + `Idempotent-Replayed: true` se a chave já foi usada |
| `GET /applications/{id}` | `{application, events[]}`: histórico completo |
| `PUT /applications/{id}` | Edita os dados. Exige `version` |
| `DELETE /applications/{id}` | 204 |
| `POST /applications/{id}/transitions` | `{toState, source, evidence, confidence?, occurredAt?, nextAction?, nextDueAt?}`. 200 `APPLIED` ou `NOTED`. 202 `SUGGESTED` quando a confiança é baixa. 409 `invalid_transition` |
| `POST /applications/{id}/notes` | `{text}`: anotação da pessoa |
| `POST /applications/{id}/suggestions/{eventId}/accept` | Aplica a sugestão, conferindo de novo a transição |
| `POST /applications/{id}/suggestions/{eventId}/dismiss` | 204 |

`origin`: AUTOMATION, MANUAL, LINK, EMAIL, BROWSER_EXTENSION, IMPORT, SOCIAL, REFERRAL. `source`: MANUAL, EMAIL, PORTAL, AGENT, SYSTEM, EXTENSION.

## Painel

| Rota | Descrição |
|---|---|
| `GET /dashboard/summary` | Contadores: vagas, candidaturas, enviadas, em andamento, entrevistas, testes, propostas, aprovações, reprovações, sem atualização, pendências, total por etapa |
| `GET /dashboard/pending` | Pendências ordenadas por prioridade e prazo: ACTION_REQUIRED, TECH_TEST, INTERVIEW_SOON, DEADLINE_SOON, SUGGESTION_TO_REVIEW, NO_UPDATE |

## Operação

`GET /actuator/health` é público e não mostra detalhes. `GET /actuator/prometheus` exige o papel `ADMIN`.
