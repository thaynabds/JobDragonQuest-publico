-- Preferências da conta que precisam valer em qualquer navegador (issue #126): por enquanto, acessibilidade.
create table account_setting (
    user_id      uuid not null references app_user (id) on delete cascade,
    setting_key  varchar(40) not null,
    setting_value varchar(4000) not null,
    updated_at   timestamp with time zone not null,
    primary key (user_id, setting_key)
);
