## O que muda

<!-- Uma ou duas frases. -->

Closes #

## Como foi testado

- [ ] `./mvnw verify` (API), se a API mudou
- [ ] `npm run verify` no painel e/ou no MCP, se mudaram
- [ ] teste novo que falharia sem esta mudança
- [ ] verificação manual descrita abaixo, se houver

## Checklist

- [ ] commits no padrão `tipo(escopo): descrição (#issue)`
- [ ] documentação atualizada no mesmo PR (README nos três idiomas, `docs/requisitos.md`, `docs/api.md`, `CHANGELOG.md`)
- [ ] mudança de esquema só por nova migração Flyway
- [ ] revisão de segurança: entrada validada, sem injeção, sem IDOR (recurso alheio responde 404), sem segredo ou dado pessoal em log, teste ou commit
- [ ] nenhuma linha de crédito a IA
