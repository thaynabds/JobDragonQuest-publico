-- Modo de automação por perfil (issue #112): o que a coleta faz com uma vaga compatível.
-- OFF: só guarda a vaga. REVIEW: cria candidatura recomendada na fila de revisão. AUTOMATIC: já cria como preparada.
alter table profile add column automation_mode varchar(20) not null default 'REVIEW';
