-- Cada item do perfil pode ficar de fora do currículo, sem deixar de contar na compatibilidade.
alter table profile_skill add column show_in_resume boolean not null default true;
alter table profile_language add column show_in_resume boolean not null default true;
alter table profile_entry add column show_in_resume boolean not null default true;
-- Links adicionais de um item (repositório, demonstração, verificação), um por linha.
alter table profile_entry add column extra_urls text;
