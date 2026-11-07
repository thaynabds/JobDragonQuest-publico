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
| `POST /auth/register` | `email`, `password` (12–128), `displayName`, `locale` (`pt-BR` ou `en`), `timeZone` | 201 `{accessToken, tokenType, expiresIn}` + cookie `jobdragonquest_rt` |
| `POST /auth/login` | `email`, `password` | 200, igual ao register. 401 genérico. 429 quando o limite estoura |
| `POST /auth/refresh` | cookie `jobdragonquest_rt` | 200 com novo par. Reusar o token antigo revoga a família inteira |
| `POST /auth/logout` | cookie | 204, revoga e limpa o cookie |
| `GET /me` | — | Dados da conta (sem hash) |

O cookie `jobdragonquest_rt` é `HttpOnly`, `SameSite=Strict`, `Secure` (configurável) e tem `Path=/api/v1/auth`. O token de acesso dura 15 minutos e deve ficar só em memória no front.

## Perfis

| Rota | Descrição |
|---|---|
| `GET /profiles` | Resumo de cada perfil |
| `POST /profiles` | Cria o perfil. 409 se o nome repetir |
| `GET /profiles/{id}` | Perfil completo |
| `PUT /profiles/{id}` | Substitui o perfil. Exige `version` (lock otimista, 409 `stale_version`) |
| `DELETE /profiles/{id}` | 204 |

Campos principais: `name`, `headline`, `summary`, `objective`, `currentTitle`, `seniority` (APPRENTICE, INTERN, TRAINEE, ENTRY, JUNIOR, MID, SENIOR, SPECIALIST, LEAD, COORDINATOR, MANAGER, DIRECTOR, EXECUTIVE; a compatibilidade compara por degraus equivalentes), `minMatchScore` (0–100), `preferredLanguage`.

Localização e disponibilidade: `city`, `stateRegion`, `country` (ISO 3166-1), `workAuthorization[]` (países), `requiresSponsorship`, `willingToRelocate`, `travelAvailability` (NONE, OCCASIONAL, FREQUENT), `availableFrom`, `noticePeriodDays` (0–365), `availability`, `workModes[]` (REMOTE, HYBRID, ONSITE), `employmentTypes[]` (CLT, PJ, FULL_TIME, PART_TIME, INTERNSHIP, APPRENTICE, TRAINEE, TEMPORARY, FREELANCE, VOLUNTEER).

Remuneração e links: `salaryExpectation`, `currentSalary`, `salaryPeriod` (HOURLY, MONTHLY, YEARLY), `salaryCurrency`, `linkedinUrl`, `githubUrl`, `portfolioUrl`, `websiteUrl` (só http/https).

Busca: `desiredTitles[]`, `locations[]`, `industries[]`, `includeKeywords[]`, `excludeKeywords[]`, `excludedCompanies[]`, `restrictions`.

Competências: `skills[] {name, level 1–5, years, months 0–11, days 0–30}`; a resposta traz também `totalYears`, usado na compatibilidade. Idiomas: `languages[] {name, level}` na edição (nome por extenso em português, inglês, espanhol ou no próprio idioma, inclusive Libras; 400 `unknown_language` se não for reconhecido) e `{code, level}` na resposta (ISO 639). Histórico: `entries[] {kind, title, organization, startDate, endDate, current, description, url, location, employmentType, degree, fieldOfStudy, status, credentialId, expirationDate, hours, skills}`, com `kind` EXPERIENCE, ACADEMIC_EXPERIENCE, EDUCATION, PROJECT, COURSE, CERTIFICATION, VOLUNTEER, AWARD ou PUBLICATION. Carta e respostas: `coverLetter`, `answers[] {question, answer}`. Dado sensível opcional: `disability`, `disabilityDetails`.

## Vagas e compatibilidade

| Rota | Descrição |
|---|---|
| `GET /jobs?page&size&favorite` | Paginado (`size` vai até 100). `favorite=true` lista só as favoritas |
| `POST /jobs` | 201 se a vaga é nova. 200 + `X-Duplicate-Of` se ela já existia |
| `GET/PUT /jobs/{id}` | Ler e atualizar |
| `PATCH /jobs/{id}/status` | `{status: OPEN \| CLOSED \| UNKNOWN}` |
| `PUT /jobs/{id}/favorite` | `{favorite}`: marca ou desmarca como favorita. Vaga de outra conta: 404 |
| `GET /jobs/similar?basis&minScore&limit` | Vagas parecidas com as favoritas (`basis=FAVORITES`) ou com as 10 últimas candidaturas enviadas (`APPLIED`). `minScore` 0 a 100 (padrão 30), `limit` 1 a 100. Resposta `[{job, score, basisJobId, basisTitle, sharedWords[], sharedSkills[]}]`. Nota: índice de Jaccard das palavras do cargo (pelo radical) e das competências, metade cada. Ficam de fora favoritas, vagas com candidatura e encerradas; só vagas da própria conta |
| `GET /jobs/{id}/matches` | Todos os perfis, do mais para o menos adequado |
| `GET /jobs/{id}/matches/{profileId}` | Análise de um perfil |

Resultado da análise: `score` (0–100 ou null), `recommendation` (APPLY, REVIEW, SKIP, INSUFFICIENT_DATA), `requirements[] {kind, text, skill, outcome MET|PARTIAL|UNMET|NOT_EVALUATED, reason}`, `blockers[] {code WORK_MODE|LOCATION|LANGUAGE|SENIORITY|EXCLUDED_COMPANY|EXCLUDED_KEYWORD|MISSING_KEYWORD|EMPLOYMENT_TYPE|WORK_AUTHORIZATION|SALARY, message}`. Os seis últimos vêm dos filtros do perfil: palavras e empresas comparadas como palavras inteiras, sem acento; tipo de contrato detectado no texto da vaga; país fora da autorização bloqueia vaga presencial ou híbrida e só alerta na remota; salário comparado entre mês e ano (hora só com hora, moeda diferente só alerta). Dado ausente na vaga não bloqueia. Na coleta automática, empresa excluída, palavra excluída ou nenhuma palavra obrigatória tiram a vaga antes de gravar, `strengths[]`, `warnings[]`, `titleAffinity`, `summary`.

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
| `POST /applications/batch/transitions` | `{ids (1 a 100), toState, evidence}`. Mudança manual em lote, cada candidatura na sua transação. 200 `{updated, failed[{id, code}]}`: candidatura de outra conta ou inexistente volta como `not_found`; mesma etapa vira anotação e não conta |
| `POST /applications/{id}/notes` | `{text}`: anotação da pessoa |
| `GET /applications/{id}/contacts` | Contatos do processo (recrutador, gestor, entrevistador, indicação). Só da conta dona; outra conta recebe 404 |
| `POST /applications/{id}/contacts` | `{role RECRUITER|HIRING_MANAGER|INTERVIEWER|REFERRAL|OTHER, name, position?, email?, linkedinUrl?, notes?, contacted?}`. Até 20 por candidatura (409 `contact_limit`); e-mail inválido 400 `invalid_email`. Com `contacted: true`, candidatura enviada ou confirmada vai para `RECRUITER_CONTACT` com o contato como evidência; etapa mais adiante não volta |
| `DELETE /applications/{id}/contacts/{contactId}` | Remove o contato. 204 |
| `POST /applications/{id}/suggestions/{eventId}/accept` | Aplica a sugestão, conferindo de novo a transição |
| `POST /applications/{id}/suggestions/{eventId}/dismiss` | 204 |

`origin`: AUTOMATION, MANUAL, LINK, EMAIL, BROWSER_EXTENSION, IMPORT, SOCIAL, REFERRAL. `source`: MANUAL, EMAIL, PORTAL, AGENT, SYSTEM, EXTENSION.

## Fontes vigiadas

| Rota | Descrição |
|---|---|
| `GET /sources/catalog` | Tipos de fonte e o modo de cada um: `AUTOMATIC` (Greenhouse, Lever, SmartRecruiters, Ashby) ou `ASSISTED` (LinkedIn, Instagram, Facebook, Indeed, Glassdoor, Gupy, Workday, página de carreiras, outro) |
| `GET /sources?profileId` | Fontes da conta, com filtro opcional por perfil |
| `POST /sources/bulk` | `{sources: [{name, kind, identifier \| url}], profileIds[], frequencyMinutes (15–10080), keywords[]}`: até 50 fontes de uma vez, tudo ou nada. Resposta 201 `{created[], duplicates[{index, existingId, name}]}` |
| `POST /sources/bulk-update` | `{sourceIds[], enabled?, frequencyMinutes?, setProfileIds? \| addProfileIds?/removeProfileIds?}`: mesma mudança em várias fontes. 404 se algum id não for da conta |
| `GET/PUT/DELETE /sources/{id}` | Ler, editar (exige `version`) e excluir |
| `POST /sources/{id}/run` | Coleta agora (só automáticas). 409 `assisted_source` para assistidas, 409 `collection_running` se já houver coleta |
| `POST /sources/{id}/check` | `{jobsRegistered?, note?}`: marca uma fonte assistida como conferida e agenda o próximo lembrete |
| `GET /sources/{id}/runs` | Últimas 20 execuções (coletas e conferências) |

Campos da fonte: `collectionMode`, `openUrl` (página para conferir), `nextRunAt`, `lastStatus` (OK, FAILED, CHECKED), `lastMessage`, `due`, `running`, `consecutiveFailures` e `profileIds`.

`GET /jobs?sourceId=` lista as vagas encontradas por uma fonte.

## Painel

| Rota | Descrição |
|---|---|
| `GET /dashboard/summary` | Contadores: vagas, candidaturas, enviadas, em andamento, entrevistas, testes, propostas, aprovações, reprovações, sem atualização, pendências, fontes vigiadas, fontes a conferir e total por etapa |
| `GET /dashboard/pending` | Pendências ordenadas por prioridade e prazo: ACTION_REQUIRED, TECH_TEST, INTERVIEW_SOON, DEADLINE_SOON, SUGGESTION_TO_REVIEW, SOURCE_TO_CHECK, SOURCE_FAILING, NO_UPDATE. Cada item traz `message` (em português) e campos estruturados (`suggestedState`, `confidence`, `detail`, `sourceId`, `url`) para o cliente montar a frase no idioma dele |

## Tokens de acesso pessoal

| Rota | Descrição |
|---|---|
| `GET /tokens` | Tokens da conta, sem o valor: `name`, `prefix` (início do token), `createdAt`, `expiresAt`, `lastUsedAt`, `revokedAt` e `active` |
| `POST /tokens` | `{name, days?}` (nome de 1 a 80 caracteres; validade de 1 a 365 dias, padrão 90). Resposta 201 `{token, value}`: é a única vez em que o valor aparece. 409 `too_many_tokens` com 10 ativos |
| `DELETE /tokens/{id}` | Revoga na hora. 404 se o token não for da conta |

O token tem o formato `jdq_` seguido de 43 caracteres e vai no cabeçalho `Authorization: Bearer jdq_…`, como o JWT. O banco guarda só o hash SHA-256. Token revogado, vencido, desconhecido ou malformado recebe 401, sem dizer o motivo. As rotas de `/tokens` exigem login no painel: com um token elas respondem 403 `session_required`, então quem obtiver um token não consegue criar outros para continuar na conta depois da revogação.

## Avisos

| Rota | Descrição |
|---|---|
| `GET /notifications?unreadOnly&page&size` | Histórico do sino (até 50 por página) e `unreadCount`. Verifica pendências antes de responder |
| `GET /notifications/due` | Avisos esperando entrega (pop-up, sistema ou som), inclusive os adiados cujo prazo passou. O painel consulta a cada poucos segundos |
| `POST /notifications/delivered` | `{ids[]}` (1 a 100): marca como entregues. Ids de outra conta são ignorados |
| `POST /notifications/{id}/read` · `POST /notifications/read-all` | Marca como lido |
| `POST /notifications/{id}/snooze` | `{minutes}` (5 a 1440): volta como não lido depois do prazo |
| `POST /notifications/test` | Cria um aviso de teste com todos os canais, para conferir pop-up, som e a permissão do navegador |
| `GET/PUT /notifications/preferences` | Preferências por tópico e horário de silêncio. Tópicos: `STAGE:<ETAPA>` (19), `ACTION_REQUIRED`, `TECH_TEST`, `INTERVIEW_REMINDER` e `DEADLINE_REMINDER` (com `leadMinutes`, até 5 valores de 5 a 20160), `SUGGESTION_TO_REVIEW`, `SOURCE_FAILING`, `SOURCE_TO_CHECK`, `NO_UPDATE` (com `staleDays`, 1 a 90) e `NEW_JOBS:<id da fonte>` (com `minScore`, 0 a 100). Cada preferência tem `enabled`, `popup`, `system` e `sound`. O silêncio usa `quietStart` e `quietEnd` (HH:mm) no fuso da conta, com `quietAllowHigh` e `hideDetails` |

Cada aviso traz `message` (pronta, em português) e campos estruturados (`type`, `state`, `company`, `title`, `detail`, `dueAt`, `itemCount`, `leadMinutes`) para o cliente montar a frase no idioma dele. Mudanças de etapa feitas pela própria pessoa não geram aviso. O mesmo fato nunca vira dois avisos (chave única por conta), e avisos lidos com mais de 90 dias são apagados.

## Operação

`GET /actuator/health` é público e não mostra detalhes. `GET /actuator/prometheus` exige o papel `ADMIN`.

### Modo de automação do perfil

`automationMode` no perfil (`OFF`, `REVIEW` padrão, `AUTOMATIC`) decide o que a coleta automática faz com uma vaga compatível (recomendação `APPLY`): `OFF` só guarda a vaga; `REVIEW` cria a candidatura como `RECOMMENDED`, que aparece na fila de revisão do painel (`GET /applications?state=RECOMMENDED`; aprovar e descartar usam `POST /applications/batch/transitions` para `PREPARED` ou `CLOSED`); `AUTOMATIC` cria direto como `PREPARED`. Nenhum modo envia candidatura a site: o envio é sempre da pessoa.
