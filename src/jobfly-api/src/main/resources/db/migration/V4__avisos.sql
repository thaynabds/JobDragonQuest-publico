-- Avisos (notificações) configuráveis por etapa, por tipo de pendência e por fonte vigiada.

create table notification_settings (
    user_id          uuid primary key references app_user (id) on delete cascade,
    quiet_enabled    boolean     not null,
    quiet_start      varchar(5)  not null,
    quiet_end        varchar(5)  not null,
    quiet_allow_high boolean     not null,
    hide_details     boolean     not null,
    updated_at       timestamp with time zone not null
);

create table notification_preference (
    user_id      uuid        not null references app_user (id) on delete cascade,
    topic        varchar(80) not null,
    enabled      boolean     not null,
    popup        boolean     not null,
    system_alert boolean     not null,
    sound        boolean     not null,
    lead_minutes varchar(60),
    min_score    integer,
    stale_days   integer,
    primary key (user_id, topic)
);

create table notification (
    id             uuid primary key,
    user_id        uuid         not null references app_user (id) on delete cascade,
    topic          varchar(80)  not null,
    type           varchar(40)  not null,
    priority       varchar(10)  not null,
    dedupe_key     varchar(200) not null,
    application_id uuid,
    source_id      uuid,
    state          varchar(40),
    company        varchar(200),
    title          varchar(300),
    detail         varchar(500),
    message        varchar(700) not null,
    due_at         timestamp with time zone,
    item_count     integer,
    lead_minutes   integer,
    popup          boolean      not null,
    system_alert   boolean      not null,
    sound          boolean      not null,
    quiet          boolean      not null,
    created_at     timestamp with time zone not null,
    delivered_at   timestamp with time zone,
    read_at        timestamp with time zone,
    snoozed_until  timestamp with time zone,
    constraint uk_notification_dedupe unique (user_id, dedupe_key)
);
create index ix_notification_user_created on notification (user_id, created_at);
