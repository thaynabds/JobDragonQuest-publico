-- Perfil profissional completo: dados que perfis profissionais e formulários de vaga costumam pedir.
-- Tudo opcional: perfis existentes continuam válidos.

alter table profile add column headline varchar(220);
alter table profile add column summary varchar(4000);
alter table profile add column current_title varchar(160);
alter table profile add column city varchar(120);
alter table profile add column state_region varchar(120);
alter table profile add column country varchar(2);
alter table profile add column requires_sponsorship boolean;
alter table profile add column willing_to_relocate boolean;
alter table profile add column travel_availability varchar(20);
alter table profile add column notice_period_days integer;
alter table profile add column available_from date;
alter table profile add column current_salary numeric(12, 2);
alter table profile add column salary_period varchar(10);
alter table profile add column linkedin_url varchar(2048);
alter table profile add column github_url varchar(2048);
alter table profile add column portfolio_url varchar(2048);
alter table profile add column website_url varchar(2048);
alter table profile add column cover_letter varchar(8000);
alter table profile add column disability boolean;
alter table profile add column disability_details varchar(500);

-- Tempo de experiência em anos, meses e dias. "years" continua como total em anos para a compatibilidade.
alter table profile_skill add column exp_years integer;
alter table profile_skill add column exp_months integer;
alter table profile_skill add column exp_days integer;
update profile_skill set exp_years = floor(years), exp_months = round((years - floor(years)) * 12), exp_days = 0
 where years is not null;

alter table profile_entry add column location varchar(160);
alter table profile_entry add column employment_type varchar(20);
alter table profile_entry add column is_current boolean;
alter table profile_entry add column degree varchar(20);
alter table profile_entry add column field_of_study varchar(160);
alter table profile_entry add column status varchar(20);
alter table profile_entry add column credential_id varchar(120);
alter table profile_entry add column expiration_date date;
alter table profile_entry add column hours integer;
alter table profile_entry add column skills varchar(500);

create table profile_work_country (
    profile_id uuid not null references profile (id) on delete cascade,
    position   integer not null,
    country    varchar(2) not null,
    primary key (profile_id, position)
);

create table profile_employment_type (
    profile_id      uuid not null references profile (id) on delete cascade,
    employment_type varchar(20) not null,
    primary key (profile_id, employment_type)
);

create table profile_excluded_company (
    profile_id uuid not null references profile (id) on delete cascade,
    position   integer not null,
    company    varchar(160) not null,
    primary key (profile_id, position)
);

create table profile_include_keyword (
    profile_id uuid not null references profile (id) on delete cascade,
    position   integer not null,
    keyword    varchar(80) not null,
    primary key (profile_id, position)
);

create table profile_exclude_keyword (
    profile_id uuid not null references profile (id) on delete cascade,
    position   integer not null,
    keyword    varchar(80) not null,
    primary key (profile_id, position)
);

create table profile_industry (
    profile_id uuid not null references profile (id) on delete cascade,
    position   integer not null,
    industry   varchar(120) not null,
    primary key (profile_id, position)
);

create table profile_answer (
    profile_id uuid not null references profile (id) on delete cascade,
    position   integer not null,
    question   varchar(500) not null,
    answer     varchar(4000) not null,
    primary key (profile_id, position)
);
