-- Envio automático pela extensão (issue #246): fica ligado por conta, só depois do aprendizado com o envio de 1 clique.
create table envio_automatico (
    user_id         uuid primary key references app_user (id) on delete cascade,
    ativo           boolean not null default false,
    aviso_aceito_em timestamp with time zone,
    atualizado_em   timestamp with time zone not null
);
