-- Base de perguntas de triagem: categoria e data da última edição.
alter table profile_answer add column category varchar(60);
alter table profile_answer add column updated_on date;

-- Acompanhamento de concursos (docs/concursos.md). CPF e senha nunca são guardados.
create table contest (
    id           uuid primary key,
    user_id      uuid not null references app_user (id) on delete cascade,
    name         varchar(200) not null,
    organ        varchar(200),
    board        varchar(120),
    notice       varchar(120),
    role_name    varchar(200),
    state_code   varchar(2),
    city         varchar(120),
    registration varchar(40),
    search_name  varchar(160),
    valid_until  date,
    notes        text,
    stages       text not null,
    created_at   timestamp with time zone not null,
    updated_at   timestamp with time zone not null
);
create index idx_contest_user on contest (user_id);

create table contest_watch (
    id              uuid primary key,
    contest_id      uuid not null references contest (id) on delete cascade,
    url             varchar(2048) not null,
    label           varchar(120),
    last_hash       varchar(64),
    last_checked_at timestamp with time zone,
    status          varchar(20) not null,
    created_at      timestamp with time zone not null,
    unique (contest_id, url)
);

create table contest_document (
    id         uuid primary key,
    contest_id uuid not null references contest (id) on delete cascade,
    url        varchar(2048) not null,
    hash       varchar(64) not null,
    status     varchar(20) not null,
    read_at    timestamp with time zone not null,
    unique (contest_id, hash)
);
create index idx_contest_document_url on contest_document (contest_id, url);

create table contest_match (
    id         uuid primary key,
    contest_id uuid not null references contest (id) on delete cascade,
    url        varchar(2048) not null,
    kind       varchar(20) not null,
    confidence varchar(10) not null,
    line_text  varchar(400) not null,
    found_at   timestamp with time zone not null
);
create index idx_contest_match_contest on contest_match (contest_id);
