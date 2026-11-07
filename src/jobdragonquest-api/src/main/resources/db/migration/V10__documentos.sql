-- Documentos do perfil (currículos com versão, cartas, certificados) e anexos das candidaturas (issue #124).
-- Ficam no banco local da própria pessoa; somem junto com o perfil, a candidatura ou a conta.
create table stored_document (
    id              uuid primary key,
    user_id         uuid not null references app_user (id) on delete cascade,
    profile_id      uuid references profile (id) on delete cascade,
    application_id  uuid references job_application (id) on delete cascade,
    kind            varchar(20) not null,
    name            varchar(200) not null,
    content_type    varchar(100) not null,
    size_bytes      bigint not null,
    sha256          varchar(64) not null,
    version         integer not null,
    data            bytea not null,
    extracted_text  varchar(100000),
    created_at      timestamp with time zone not null
);
create index ix_document_profile on stored_document (user_id, profile_id);
create index ix_document_application on stored_document (user_id, application_id);
