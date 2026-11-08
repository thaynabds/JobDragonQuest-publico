# ADR-009: Repositório principal privado e espelho público

- **Situação:** Aceita
- **Desde:** v0.3.1

## Contexto

O repositório de trabalho guarda, fora dos arquivos versionados, anotações e dados de teste que não podem ser públicos, mas o projeto precisa ser avaliado publicamente.

## Decisão

Espelho público com histórico limpo, publicado só por `scripts/publicar-espelho.sh`, que audita vazamento, e-mails e notas antes de enviar `main`, `develop` e tags. O histórico de issues e PRs é exportado para `docs/historico-issues-prs.md`.

## Alternativas consideradas

Tornar o principal público (exporia o histórico); reescrever o histórico do principal (perderia rastreabilidade).

## Consequências

Issues e PRs não aparecem no espelho, que tem a evidência exportada ([governanca.md](../governanca.md)); CI de PR só no principal.
