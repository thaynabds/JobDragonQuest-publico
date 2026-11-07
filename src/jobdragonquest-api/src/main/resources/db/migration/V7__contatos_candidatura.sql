-- Contatos de recrutadores, gestores, entrevistadores e indicações ligados a uma candidatura (issue #108).
-- São dados de terceiros: ficam só na conta dona, somem junto com a candidatura e nunca vão para log.
create table application_contact (
    id              uuid primary key,
    application_id  uuid not null references job_application (id) on delete cascade,
    role            varchar(20) not null,
    name            varchar(120) not null,
    position        varchar(120),
    email           varchar(254),
    linkedin_url    varchar(500),
    notes           varchar(2000),
    contacted_at    timestamp with time zone,
    created_at      timestamp with time zone not null
);
create index ix_contact_application on application_contact (application_id, created_at);
