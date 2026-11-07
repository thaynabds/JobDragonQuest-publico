# Como contribuir

Este guia vale para quem abre issue ou PR e para as ferramentas de IA que trabalham no repositório (ver também
[AGENTS.md](AGENTS.md)). O código é público para consulta; leia a [licença](LICENSE) antes de reutilizar qualquer parte.

## Preparar o ambiente

Requisitos: Java 21, Node 20.19+ e PostgreSQL 14+ (o instalador cuida do banco local, sem sudo).

```bash
scripts/instalar.sh
```

Para rodar depois: `scripts/iniciar.sh`. Detalhes no [README](README.md#instalação-rápida).

## Fluxo de branches (Git Flow)

- `main` só recebe releases e hotfixes. `develop` é a integração. Nada é commitado direto nelas.
- Toda mudança começa por uma issue. A branch sai da `develop` com o número da issue:
  - `feature/issue-NN-descricao` para funcionalidade ou documentação;
  - `fix/issue-NN-descricao` para correção na `develop`;
  - `hotfix/X.Y.Z` para correção urgente a partir da `main`;
  - `release/X.Y.Z` para preparar uma versão.
- O PR vai para a `develop` com `Closes #NN` na descrição e só entra com o CI verde.
- A release sai da `develop` para a `main`, ganha tag anotada `vX.Y.Z` e a `main` volta para a `develop`.

## Commits

Conventional Commits citando a issue:

```text
tipo(escopo): descrição no imperativo, em minúsculas (#NN)
```

- Tipos: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `ci`, `build`, `chore`, `style`, `revert`.
- Escopo opcional, curto: `sources`, `web`, `mcp`, `api`, `scripts`, `docs`...
- Um commit faz uma coisa. Se a mensagem precisa de "e", provavelmente são dois commits.
- Mudança que quebra compatibilidade: `feat(api)!: ...` e um parágrafo `BREAKING CHANGE:` no corpo.
- Nenhuma linha de coautoria ou crédito a IA.

O CI confere as mensagens dos commits de cada PR com `scripts/verificar/commits.mjs`.

## Testes

- Todo comportamento novo vem com um teste que falharia sem ele. Toda correção vem com o teste que reproduz o defeito.
- API: `cd src/jobdragonquest-api && ./mvnw verify` (testes e cobertura mínima de 80% nos pacotes críticos).
- Painel: `cd src/jobdragonquest-web && npm run verify` (lint, tipos, testes e build).
- MCP: `cd src/jobdragonquest-mcp && npm run verify`.
- Plugin do Congo: `python -m unittest discover -s integrations/congo -p 'test_*.py'`.
- Extensão: `node --test extensao-navegador/comum.test.mjs`.
- Sistema rodando: `scripts/testar-api.py`, `scripts/testar-avisos.py`, `scripts/testar-coleta.py`.

## Banco de dados

Mudança de esquema só por nova migração Flyway em `src/jobdragonquest-api/src/main/resources/db/migration`. Migração
publicada nunca é editada.

## Documentação

A documentação muda no mesmo PR que o código. A regra completa está em [docs/README.md](docs/README.md). Em resumo:
requisito novo ou alterado atualiza `docs/requisitos.md` (com issue e teste), rota nova atualiza `docs/api.md`, e toda
mudança visível entra no `CHANGELOG.md` e nos READMEs em português, inglês e espanhol.

## Segurança

Antes de pedir revisão, revise como atacante: entrada não validada, injeção (SQL, comando, caminho, HTML), SSRF, IDOR
(recurso de outro usuário responde 404), segredos ou dados pessoais em log, teste, commit ou resposta de API. Configuração
nova entra por variável de ambiente, documentada em `.env.example` sem valor. Falha de segurança se relata em particular,
como explica o [SECURITY.md](SECURITY.md).
