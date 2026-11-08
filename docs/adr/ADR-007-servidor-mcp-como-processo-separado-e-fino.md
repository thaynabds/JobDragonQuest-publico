# ADR-007: Servidor MCP como processo separado e fino

- **Situação:** Aceita
- **Desde:** v0.3.0

## Contexto

A IA da própria pessoa precisa usar o JobDragonQuest sem ganhar acesso direto ao banco e sem ação destrutiva.

## Decisão

`jobdragonquest-mcp` fala stdio com o cliente de IA e chama a API REST com um token de acesso pessoal (`jdq_…`), revogável, guardado só como hash, que não cria outros tokens. Não há ferramenta de exclusão; argumentos são validados e respostas marcadas como dado não confiável.

## Alternativas consideradas

MCP dentro da API (mais superfície no processo principal); acesso direto ao banco (fura a autorização).

## Consequências

O MCP herda toda a autorização e o isolamento por usuário da API (`PersonalTokenIntegrationTest`, `mcp.test.ts`).
