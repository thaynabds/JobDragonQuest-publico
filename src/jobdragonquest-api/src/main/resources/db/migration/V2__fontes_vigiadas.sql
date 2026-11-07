-- Fontes vigiadas: sites, ATS e redes sociais que o usuário quer acompanhar, ligados a um ou mais perfis.

create table watched_source (
    id                    uuid primary key,
    user_id               uuid not null references app_user (id) on delete cascade,
    name                  varchar(120) not null,
    kind                  varchar(30) not null,
    collection_mode       varchar(20) not null,
    identifier            varchar(100),
    url                   varchar(2048),
    dedup_key             varchar(64) not null,
    frequency_minutes     integer not null,
    enabled               boolean not null,
    next_run_at           timestamp with time zone not null,
    last_run_at           timestamp with time zone,
    last_status           varchar(20),
    last_message          varchar(500),
    last_found            integer,
    consecutive_failures  integer not null default 0,
    created_at            timestamp with time zone not null,
    updated_at            timestamp with time zone not null,
    version               bigint not null,
    constraint uk_watched_source_dedup unique (user_id, dedup_key)
);
create index ix_watched_source_due on watched_source (enabled, collection_mode, next_run_at);

create table watched_source_profile (
    source_id   uuid not null references watched_source (id) on delete cascade,
    profile_id  uuid not null references profile (id) on delete cascade,
    primary key (source_id, profile_id)
);
create index ix_watched_source_profile_profile on watched_source_profile (profile_id);

create table watched_source_keyword (
    source_id  uuid not null references watched_source (id) on delete cascade,
    position   integer not null,
    keyword    varchar(80) not null,
    primary key (source_id, position)
);

create table source_run (
    id           uuid primary key,
    source_id    uuid not null references watched_source (id) on delete cascade,
    trigger_type varchar(20) not null,
    status       varchar(20) not null,
    found        integer not null,
    created      integer not null,
    recommended  integer not null,
    message      varchar(500),
    started_at   timestamp with time zone not null,
    finished_at  timestamp with time zone not null
);
create index ix_source_run_source on source_run (source_id, started_at);

alter table job add column watched_source_id uuid references watched_source (id) on delete set null;
