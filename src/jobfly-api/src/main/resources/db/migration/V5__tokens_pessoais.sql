-- Tokens de acesso pessoal para integrações (servidor MCP, plugin do Congo). Só o hash SHA-256 fica no banco.
create table personal_token (
    id           uuid primary key,
    user_id      uuid        not null references app_user (id) on delete cascade,
    name         varchar(80) not null,
    token_hash   varchar(64) not null,
    prefix       varchar(16) not null,
    created_at   timestamp with time zone not null,
    expires_at   timestamp with time zone not null,
    last_used_at timestamp with time zone,
    revoked_at   timestamp with time zone,
    constraint uk_personal_token_hash unique (token_hash)
);
create index ix_personal_token_user on personal_token (user_id);
