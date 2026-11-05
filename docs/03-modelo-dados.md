# 03. Modelo de dados

Migração: `jobfly-api/src/main/resources/db/migration/V1__schema_inicial.sql` (Flyway). O Hibernate só **valida** o esquema (`ddl-auto: validate`): toda mudança passa por uma nova migração versionada.

## Convenções

- Chave primária `uuid` gerada na aplicação. As entidades estendem `AssignedIdEntity`, que implementa `Persistable` para o Spring Data usar `persist`, e não `merge`.
- Datas em `timestamp with time zone`, sempre UTC (`hibernate.jdbc.time_zone=UTC`). A interface converte para o fuso do usuário (`app_user.time_zone`).
- Enums gravados como texto (`varchar`), legíveis no banco e estáveis a reordenações.
- Todas as tabelas de negócio têm `user_id`, e toda consulta filtra por ele (isolamento por usuário).
- `@Version` (lock otimista) em `profile` e `job_application`: duas abas não sobrescrevem a edição uma da outra.

## Tabelas

```
app_user 1─┬─* refresh_token          (hash SHA-256, família, revogação)
           ├─* profile 1─┬─* profile_title / profile_location / profile_work_mode
           │             ├─* profile_skill      (nome, normalizado, nível 1-5, anos)
           │             ├─* profile_language   (ISO 639-1, CEFR)
           │             └─* profile_entry      (experiência, formação, projeto, curso, certificação)
           ├─* job 1─┬─* job_requirement        (MANDATORY | DESIRED, competência, anos)
           │         └─* job_language           (código, nível mínimo, exigido?)
           ├─* job_application 1─* application_event
           └─* audit_log
```

| Tabela | Propósito | Chaves de unicidade e índices |
|---|---|---|
| `app_user` | Conta, senha Argon2id, bloqueio por tentativas | `email` único (minúsculo) |
| `refresh_token` | Sessões renováveis; só o hash é guardado | `token_hash` único, índice por `family_id` |
| `profile` | Perfil profissional independente | `(user_id, name)` único |
| `job` | Vaga encontrada ou registrada | `(user_id, url_hash)` único; índice `(user_id, company_norm, title_norm)` |
| `job_application` | Candidatura de qualquer origem | `(user_id, idempotency_key)` único; índices por estado, URL, empresa/cargo |
| `application_event` | Histórico: mudança de etapa, nota ou sugestão, com origem, evidência e confiança | índice `(application_id, created_at)` |
| `audit_log` | Quem fez o quê e quando (sem conteúdo sensível) | índice `(user_id, created_at)` |

## Deduplicação

| Entidade | Critério |
|---|---|
| Vaga | 1) mesma URL canônica (hash SHA-256); 2) mesma empresa e cargo normalizados no mesmo local, quando não há duas URLs canônicas diferentes |
| Candidatura | 1) mesma `Idempotency-Key`; 2) mesma URL canônica; 3) mesma empresa e cargo normalizados nos últimos 60 dias (configurável) |

A URL canônica remove `utm_*`, `gclid`, `fbclid`, `trk`, `ref`, fragmento, `www.` e barra final. No LinkedIn, `/jobs/view/<slug>-<id>`, `/jobs/view/<id>` e `?currentJobId=<id>` viram uma só chave. Na empresa, sufixos societários (Ltda, S.A., Inc...) são ignorados.

## Máquina de estados da candidatura

Fases: `PREPARATION` (JOB_FOUND → AWAITING_USER), `INTAKE` (SUBMITTED, CONFIRMED), `PROCESS` (SCREENING … ADDITIONAL_STAGE, em qualquer ordem), `OFFER` (OFFER_RECEIVED) e `FINAL` (APPROVED, REJECTED, CLOSED).

| Fonte | Pode |
|---|---|
| `MANUAL` (a pessoa) | Qualquer mudança, inclusive reabrir um estado final |
| `EMAIL`, `PORTAL`, `AGENT`, `SYSTEM`, `EXTENSION` | Só avançar. Nunca sai de estado final, não volta para envio ou preparação, não reprova o que não foi enviado |

Abaixo de `min-automatic-confidence` (0,70), a mudança vira um evento `SUGGESTION`/`PENDING` e a etapa não muda. "Sem atualização" (`stale`) é calculado: candidatura ativa sem evidência há mais de 21 dias. Nunca é um estado gravado.

## Retenção e LGPD

- `on delete cascade` a partir de `app_user`: excluir a conta apaga perfis, vagas, candidaturas, eventos e tokens.
- `audit_log.user_id` não tem chave estrangeira: o rastro de auditoria sobrevive à exclusão, mas guarda só ids e nomes de ação. A política de expurgo está no roadmap.
