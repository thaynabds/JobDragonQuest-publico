# ADR-004: Trabalho em segundo plano com agendador e reserva no banco

- **Situação:** Aceita
- **Desde:** v0.2.0

## Contexto

A coleta de vagas roda em segundo plano, dezenas a centenas de vezes por dia, e uma fonte nunca pode ser coletada por duas execuções ao mesmo tempo (RN10).

## Decisão

Agendador do Spring que coleta poucas fontes vencidas por vez, cada uma reservada antes no próprio PostgreSQL (reserva com validade, liberada se a coleta for interrompida), com recuo exponencial em falha.

## Alternativas consideradas

RabbitMQ ou Kafka (infraestrutura extra sem volume que justifique); fila em tabela com `SKIP LOCKED` (fica para quando houver vários tipos de tarefa).

## Consequências

Sem serviço extra para instalar; concorrência coberta por `SourceCollectorRaceTest`. Uma fila de mensagens entra se a coleta virar serviço próprio (ADR-001).
