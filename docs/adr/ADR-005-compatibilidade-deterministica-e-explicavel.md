# ADR-005: Compatibilidade determinística e explicável

- **Situação:** Aceita
- **Desde:** v0.1.0

## Contexto

A recomendação de candidatura precisa ser auditável e nunca inventar dado do perfil (RN01, RN02).

## Decisão

Nota de 0 a 100 calculada por regras (requisitos obrigatórios e desejáveis, impedimentos eliminatórios separados), sempre com justificativa por requisito. Nenhum provedor de IA é necessário.

## Alternativas consideradas

Nota decidida por um modelo de linguagem (não auditável e dependente de provedor pago).

## Consequências

Funciona offline e é testável (`MatchingEngineTest`). Uma camada de IA opcional pode, no futuro, só extrair requisitos de texto livre; ela não decide a nota.
