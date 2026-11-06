-- Reserva de execução por fonte: só uma coleta (manual ou agendada) roda por vez. Uma reserva vencida é ignorada,
-- então uma coleta interrompida (ex.: a API caiu no meio) não trava a fonte para sempre.
alter table watched_source add column running_until timestamp with time zone;
