-- Token de acesso pessoal sem validade (expires_at nulo) e opções extras do perfil (JSON validado na API).
alter table personal_token alter column expires_at drop not null;
alter table profile add column options text;
-- Aviso excluído pela pessoa: a linha fica para o aviso repetido não ser criado de novo.
alter table notification add column dismissed_at timestamp with time zone;
-- Códigos de recuperação de conta: guardados só como hash, cada um vale uma vez.
create table recovery_code (
    id         uuid primary key,
    user_id    uuid not null references app_user (id) on delete cascade,
    code_hash  varchar(64) not null unique,
    used_at    timestamp with time zone,
    created_at timestamp with time zone not null
);
create index idx_recovery_code_user on recovery_code (user_id);
