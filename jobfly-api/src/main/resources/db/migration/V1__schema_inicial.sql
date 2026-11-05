-- JobFly, esquema inicial. Datas sempre em UTC (timestamp with time zone).
-- Compatível com PostgreSQL 14+ (produção) e H2 em modo PostgreSQL (testes).

create table app_user (
    id              uuid primary key,
    email           varchar(254) not null,
    password_hash   varchar(255) not null,
    display_name    varchar(120) not null,
    role            varchar(20)  not null,
    locale          varchar(10)  not null,
    time_zone       varchar(64)  not null,
    failed_logins   integer      not null default 0,
    locked_until    timestamp with time zone,
    created_at      timestamp with time zone not null,
    updated_at      timestamp with time zone not null,
    constraint uk_app_user_email unique (email)
);

create table refresh_token (
    id          uuid primary key,
    user_id     uuid not null references app_user (id) on delete cascade,
    family_id   uuid not null,
    token_hash  varchar(64) not null,
    expires_at  timestamp with time zone not null,
    revoked_at  timestamp with time zone,
    created_at  timestamp with time zone not null,
    constraint uk_refresh_token_hash unique (token_hash)
);
create index ix_refresh_token_family on refresh_token (family_id);

create table profile (
    id                  uuid primary key,
    user_id             uuid not null references app_user (id) on delete cascade,
    name                varchar(120) not null,
    objective           varchar(4000),
    seniority           varchar(20) not null,
    min_match_score     integer not null,
    salary_expectation  numeric(12, 2),
    salary_currency     varchar(3),
    availability        varchar(500),
    restrictions        varchar(2000),
    preferred_language  varchar(8) not null,
    created_at          timestamp with time zone not null,
    updated_at          timestamp with time zone not null,
    version             bigint not null,
    constraint uk_profile_user_name unique (user_id, name)
);
create index ix_profile_user on profile (user_id);

create table profile_title (
    profile_id  uuid not null references profile (id) on delete cascade,
    position    integer not null,
    title       varchar(160) not null,
    primary key (profile_id, position)
);

create table profile_location (
    profile_id  uuid not null references profile (id) on delete cascade,
    position    integer not null,
    location    varchar(160) not null,
    primary key (profile_id, position)
);

create table profile_work_mode (
    profile_id  uuid not null references profile (id) on delete cascade,
    work_mode   varchar(20) not null,
    primary key (profile_id, work_mode)
);

create table profile_skill (
    profile_id  uuid not null references profile (id) on delete cascade,
    position    integer not null,
    name        varchar(80) not null,
    normalized  varchar(80) not null,
    level       integer not null,
    years       numeric(4, 1),
    primary key (profile_id, position)
);

create table profile_language (
    profile_id  uuid not null references profile (id) on delete cascade,
    position    integer not null,
    code        varchar(8) not null,
    level       varchar(10) not null,
    primary key (profile_id, position)
);

create table profile_entry (
    profile_id    uuid not null references profile (id) on delete cascade,
    position      integer not null,
    kind          varchar(20) not null,
    title         varchar(200) not null,
    organization  varchar(200),
    start_date    date,
    end_date      date,
    description   varchar(4000),
    url           varchar(2048),
    primary key (profile_id, position)
);

create table job (
    id               uuid primary key,
    user_id          uuid not null references app_user (id) on delete cascade,
    source           varchar(40) not null,
    source_url       varchar(2048),
    canonical_url    varchar(2048),
    url_hash         varchar(64),
    company          varchar(200) not null,
    company_norm     varchar(200) not null,
    title            varchar(200) not null,
    title_norm       varchar(200) not null,
    location         varchar(200),
    country          varchar(2),
    work_mode        varchar(20),
    seniority        varchar(20),
    description      varchar(20000),
    salary_min       numeric(12, 2),
    salary_max       numeric(12, 2),
    salary_currency  varchar(3),
    salary_period    varchar(10),
    status           varchar(20) not null,
    found_at         timestamp with time zone not null,
    updated_at       timestamp with time zone not null,
    constraint uk_job_user_url unique (user_id, url_hash)
);
create index ix_job_user_company_title on job (user_id, company_norm, title_norm);

create table job_requirement (
    job_id     uuid not null references job (id) on delete cascade,
    position   integer not null,
    kind       varchar(20) not null,
    text       varchar(500) not null,
    skill      varchar(80),
    min_years  numeric(4, 1),
    primary key (job_id, position)
);

create table job_language (
    job_id     uuid not null references job (id) on delete cascade,
    position   integer not null,
    code       varchar(8) not null,
    min_level  varchar(10) not null,
    required   boolean not null,
    primary key (job_id, position)
);

create table job_application (
    id                uuid primary key,
    user_id           uuid not null references app_user (id) on delete cascade,
    job_id            uuid references job (id) on delete set null,
    profile_id        uuid references profile (id) on delete set null,
    origin            varchar(20) not null,
    platform          varchar(80),
    company           varchar(200) not null,
    company_norm      varchar(200) not null,
    title             varchar(200) not null,
    title_norm        varchar(200) not null,
    job_url           varchar(2048),
    url_hash          varchar(64),
    country           varchar(2),
    applied_at        timestamp with time zone,
    state             varchar(40) not null,
    state_changed_at  timestamp with time zone not null,
    last_evidence_at  timestamp with time zone not null,
    next_action       varchar(500),
    next_due_at       timestamp with time zone,
    match_score       integer,
    notes             varchar(4000),
    idempotency_key   varchar(100),
    created_at        timestamp with time zone not null,
    updated_at        timestamp with time zone not null,
    version           bigint not null,
    constraint uk_application_idempotency unique (user_id, idempotency_key)
);
create index ix_application_user_state on job_application (user_id, state);
create index ix_application_user_url on job_application (user_id, url_hash);
create index ix_application_user_company_title on job_application (user_id, company_norm, title_norm);

create table application_event (
    id              uuid primary key,
    application_id  uuid not null references job_application (id) on delete cascade,
    kind            varchar(20) not null,
    from_state      varchar(40),
    to_state        varchar(40),
    source          varchar(20) not null,
    evidence        varchar(4000) not null,
    confidence      numeric(3, 2) not null,
    status          varchar(20) not null,
    occurred_at     timestamp with time zone not null,
    created_at      timestamp with time zone not null
);
create index ix_event_application on application_event (application_id, created_at);

create table audit_log (
    id           uuid primary key,
    user_id      uuid,
    action       varchar(60) not null,
    entity_type  varchar(40),
    entity_id    uuid,
    details      varchar(500),
    created_at   timestamp with time zone not null
);
create index ix_audit_user on audit_log (user_id, created_at);
