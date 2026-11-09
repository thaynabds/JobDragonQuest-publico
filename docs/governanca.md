# Governança: repositório principal e espelho público

## Os dois repositórios

| Repositório | Visibilidade | O que tem |
|---|---|---|
| `thaynabds/JobDragonQuest` | privado | onde o trabalho acontece: issues, branches de trabalho, PRs, revisões e CI de cada PR |
| [`thaynabds/JobDragonQuest-publico`](https://github.com/thaynabds/JobDragonQuest-publico) | público | espelho: só `main`, `develop` e as tags `vX.Y.Z`, com o mesmo conteúdo e o CI rodando de novo |

**Por quê.** O repositório principal guarda, fora dos arquivos versionados, anotações de trabalho e dados de teste da
autora que não devem ser públicos. Reescrever o histórico do principal arriscaria perder rastreabilidade; por isso o
público é um espelho de histórico limpo, publicado por `scripts/publicar-espelho.sh`, que recusa enviar se:

1. a árvore de trabalho não estiver limpa;
2. a auditoria de vazamento (`scripts/verificar/vazamento.mjs`: segredos, dados pessoais, metadados e logs) falhar;
3. algum commit ou tag tiver e-mail que não seja o endereço privado do GitHub;
4. algum commit trouxer as notas internas.

Branches de trabalho, stashes e arquivos ignorados nunca vão para o espelho, e o envio não usa `--force`.

## Como auditar o processo pelo espelho

O GitHub não copia issues nem PRs entre repositórios. A evidência fica no próprio espelho:

- **Issues e PRs:** [historico-issues-prs.md](historico-issues-prs.md), exportado do principal a cada release, com
  número, título, estado, datas, branch de origem e destino e a issue que cada PR fecha.
- **Commits do espelho:** a partir da v0.6.0, cada publicação é um commit novo com o conteúdo auditado, sem o histórico do principal. Os commits anteriores continuam como estavam.
- **Commits:** Conventional Commits citando a issue (`feat(sources): ... (#9)`), conferidos no CI de cada PR por
  `scripts/verificar/commits.mjs`.
- **Versões:** tags anotadas SemVer, uma página de Release por versão nos dois repositórios e o [CHANGELOG](../CHANGELOG.md).
- **Rastreabilidade:** cada requisito em [requisitos.md](requisitos.md) aponta a issue e o teste que o comprovam.

## Git Flow

O fluxo completo (nomes de branch, commits, PRs, releases e hotfixes) está no [CONTRIBUTING](../CONTRIBUTING.md).

## Proteção das branches

Não há proteção técnica de branch configurada nos repositórios: a regra "nada direto na `main` nem na `develop`, só por
PR com CI verde" é de processo. A conferência é feita por `scripts/verificar/git-flow.mjs`, que confere se cada commit da
`main` chegou por merge de PR e se cada PR de trabalho fecha uma issue, e pela tabela de PRs do [histórico](historico-issues-prs.md).
