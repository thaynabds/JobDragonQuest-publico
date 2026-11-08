# Documentação do JobDragonQuest

Índice dos documentos de engenharia. Versão documentada: a da tag mais recente (ver [CHANGELOG](../CHANGELOG.md)).

| Documento | Conteúdo |
|---|---|
| [project-charter.md](project-charter.md) | Termo de abertura: objetivo, escopo, partes interessadas, riscos e critérios de sucesso |
| [requisitos.md](requisitos.md) | Requisitos funcionais e não funcionais, regras de negócio e rastreabilidade RF → issue → caso de uso → história → teste |
| [casos-de-uso.md](casos-de-uso.md) | Casos de uso com fluxo principal, alternativos e exceções |
| [user-stories.md](user-stories.md) | Histórias de usuário com critérios de aceite em Gherkin |
| [arquitetura.md](arquitetura.md) | Arquitetura, módulos e decisões (ADRs) |
| [adr/README.md](adr/README.md) | Um arquivo por decisão de arquitetura (ADR-001 a ADR-010) |
| [diagramas.md](diagramas.md) | Diagramas C4, de classes, DER e máquina de estados |
| [modelo-dados.md](modelo-dados.md) | Tabelas, chaves e migrações |
| [api.md](api.md) | API REST v1: rotas, formatos e o formato padrão de erro |
| [integracoes.md](integracoes.md) | Integrações possíveis e o que os termos de uso permitem |
| [seguranca.md](seguranca.md) | Modelo de ameaças e defesas |
| [deploy.md](deploy.md) | Deploy opcional |
| [runbook.md](runbook.md) | Operação e problemas comuns |
| [acessibilidade.md](acessibilidade.md) | Checklist de acessibilidade (WCAG 2.2 AA) |
| [roadmap.md](roadmap.md) | Situação de cada critério e próximas entregas |
| [governanca.md](governanca.md) | Repositório principal × espelho público, Git Flow e como auditar o processo |
| [historico-issues-prs.md](historico-issues-prs.md) | Histórico exportado de issues e PRs do repositório principal |
| [resumo-jobdragonquest.pdf](resumo-jobdragonquest.pdf) | Resumo do projeto em PDF |

Fora desta pasta: [README](../README.md) (também em [inglês](../README.en.md) e [espanhol](../README.es.md)),
[CONTRIBUTING](../CONTRIBUTING.md), [SECURITY](../SECURITY.md), [CHANGELOG](../CHANGELOG.md) e [LICENSE](../LICENSE).

## Regra de sincronização documentação × código

A documentação muda **no mesmo PR** que o código. O checklist do PR cobra isto:

1. Requisito novo, alterado ou entregue: atualiza a linha em `requisitos.md` com a situação, a issue e o teste que o comprova.
2. Rota nova ou alterada: atualiza `api.md`.
3. Tabela ou coluna nova: nova migração Flyway e atualização de `modelo-dados.md`.
4. Decisão de arquitetura: nova ADR em `arquitetura.md`.
5. Mudança visível para quem usa: entra no `CHANGELOG.md` e nos três READMEs.
6. Release: versão igual nos badges, nos manifestos (`pom.xml`, `package.json`) e na tag; `historico-issues-prs.md` é
   regerado com `node scripts/exportar-historico.mjs`.

`node scripts/verificar/governanca.mjs` confere os itens que dá para conferir por máquina (índice completo, versões,
rastreabilidade, changelog com todas as tags).
