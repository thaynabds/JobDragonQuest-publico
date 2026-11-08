# ADR-003: React, TypeScript estrito e Vite no painel

- **Situação:** Aceita
- **Desde:** v0.2.0

## Contexto

O painel precisa ser acessível, traduzível e fácil de manter por uma pessoa.

## Decisão

React 19 com TypeScript `strict` e Vite. Internacionalização própria (`src/jobdragonquest-web/src/i18n`, pt, en e es, com script que cria idiomas novos), sem biblioteca externa.

## Alternativas consideradas

Angular (atenderia, com mais estrutura e mais código); `react-i18next` (dependência a mais para um catálogo pequeno).

## Consequências

Poucas dependências de produção (React, React Router e uma fonte), auditadas no CI; tipos estritos pegam erros antes do teste.
