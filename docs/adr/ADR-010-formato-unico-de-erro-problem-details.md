# ADR-010: Formato único de erro (Problem Details)

- **Situação:** Aceita
- **Desde:** v0.4.1

## Contexto

O painel, o MCP e as integrações precisam tratar erros de forma previsível, sem vazar detalhe interno.

## Decisão

Toda resposta de erro segue a RFC 9457 com o campo extra `code`, estável; filtros e segurança usam o mesmo formato (`ProblemResponses`). Recurso de outra conta responde 404.

## Alternativas consideradas

Corpos de erro livres por controller (inconsistentes); 403 para recurso alheio (revela que ele existe).

## Consequências

Tradução no painel pelo `code`; nenhum stack trace, SQL ou motivo de falha de token na resposta (`ErrorFormatTest`).
