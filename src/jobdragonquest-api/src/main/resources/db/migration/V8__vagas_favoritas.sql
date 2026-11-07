-- Vagas favoritas: base da busca por vagas parecidas (issue #110).
alter table job add column favorite boolean not null default false;
create index ix_job_user_favorite on job (user_id, favorite);
