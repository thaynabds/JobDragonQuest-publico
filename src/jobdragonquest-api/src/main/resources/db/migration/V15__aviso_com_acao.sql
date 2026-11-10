-- Aviso com onde agir (link) e com o texto completo para leitura, por exemplo do e-mail que o originou.
alter table notification add column action_url varchar(2048);
alter table notification add column body text;
