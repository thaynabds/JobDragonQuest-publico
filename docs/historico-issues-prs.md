# Histórico de issues e PRs

Exportado do repositório principal (`thaynabds/JobDragonQuest`, privado) por `node scripts/exportar-historico.mjs`. O espelho
público tem o mesmo código e as mesmas tags, mas o GitHub não copia issues nem PRs entre repositórios; esta página é
a evidência auditável do processo. Só campos de processo são exportados (sem corpo, comentário ou autor).
Por que existem dois repositórios: [governanca.md](governanca.md).

Resumo: 65 issues (65 fechadas) e 68 PRs (68 com merge).

## Issues

| # | Título | Estado | Aberta | Fechada | PR que fechou | Labels |
|---|---|---|---|---|---|---|
| 1 | docs: termo de abertura do projeto (project charter) | CLOSED | 2026-11-06 | 2026-11-06 | PR 40, PR 49 | documentation |
| 2 | docs: requisitos funcionais e não funcionais rastreáveis | CLOSED | 2026-11-06 | 2026-11-06 | PR 41, PR 49 | documentation |
| 3 | docs: especificação de casos de uso | CLOSED | 2026-11-06 | 2026-11-06 | PR 42, PR 49 | documentation |
| 4 | docs: histórias de usuário com critérios de aceite em Gherkin | CLOSED | 2026-11-06 | 2026-11-06 | PR 43, PR 49 | documentation |
| 5 | docs: diagramas de arquitetura (C4), classes e DER | CLOSED | 2026-11-06 | 2026-11-06 | PR 44, PR 49 | documentation, enhancement |
| 6 | refactor: reorganizar o código em src/ conforme a estrutura obrigatória | CLOSED | 2026-11-06 | 2026-11-06 | PR 20, PR 49 | refactor, chore |
| 7 | docs: README com badges, funcionalidades e fluxo de branches | CLOSED | 2026-11-06 | 2026-11-06 | PR 46, PR 49 | documentation |
| 8 | ci: integração contínua com build e testes da API e do painel | CLOSED | 2026-11-06 | 2026-11-06 | PR 45, PR 49 | test, ci |
| 9 | feat(sources): cadastrar fontes vigiadas e configurar várias de uma vez em vários perfis | CLOSED | 2026-11-06 | 2026-11-06 | PR 21, PR 49 | feature |
| 10 | feat(sources): coletar vagas de ATS públicos (Greenhouse, Lever, SmartRecruiters, Ashby) com agendador | CLOSED | 2026-11-06 | 2026-11-06 | PR 22, PR 49 | feature |
| 11 | feat(sources): lembretes de conferência para fontes assistidas (LinkedIn, Instagram, Facebook...) | CLOSED | 2026-11-06 | 2026-11-06 | PR 23, PR 49 | feature |
| 12 | feat(web): base do painel (React + Vite + TypeScript, login, i18n pt/en) | CLOSED | 2026-11-06 | 2026-11-06 | PR 24, PR 49 | feature |
| 13 | feat(web): telas de perfis profissionais | CLOSED | 2026-11-06 | 2026-11-06 | PR 25, PR 49 | feature |
| 14 | feat(web): tela de fontes vigiadas com cadastro em lote | CLOSED | 2026-11-06 | 2026-11-06 | PR 26, PR 49 | feature |
| 15 | feat(web): vagas e análise de compatibilidade | CLOSED | 2026-11-06 | 2026-11-06 | PR 27, PR 49 | feature |
| 16 | feat(web): candidaturas com Kanban, detalhe e cadastro externo | CLOSED | 2026-11-06 | 2026-11-06 | PR 28, PR 49 | feature |
| 17 | feat(web): visão geral e central de pendências | CLOSED | 2026-11-06 | 2026-11-06 | PR 29, PR 49 | feature |
| 18 | chore(tools): unlazy e OpenCode no projeto | CLOSED | 2026-11-06 | 2026-11-06 | PR 33, PR 49 | chore |
| 19 | test: teste de fumaça da API e da coleta real | CLOSED | 2026-11-06 | 2026-11-06 | PR 30, PR 49 | test |
| 31 | fix(sources): coleta manual concorrente com o agendador | CLOSED | 2026-11-06 | 2026-11-06 | PR 34, PR 49 | bug, security |
| 32 | fix(matching): casos de borda achados na revisão do OpenCode | CLOSED | 2026-11-06 | 2026-11-06 | PR 35, PR 49 | bug |
| 36 | fix(scripts): iniciar.sh parar não encerra o painel | CLOSED | 2026-11-06 | 2026-11-06 | PR 37, PR 49 | bug |
| 38 | fix(web): cor da nota pela recomendação e pendências traduzidas | CLOSED | 2026-11-06 | 2026-11-06 | PR 39, PR 49 | bug, enhancement |
| 47 | docs: resumo do projeto em PDF | CLOSED | 2026-11-06 | 2026-11-06 | PR 48, PR 49 | documentation |
| 50 | fix: regra do AGENTS.md dispara a checagem de crédito de IA | CLOSED | 2026-11-06 | 2026-11-06 | PR 51 | bug, documentation |
| 52 | fix: verificador de Git Flow fixa a v0.2.0 como topo da main | CLOSED | 2026-11-06 | 2026-11-06 | PR 53 | bug |
| 54 | docs: README com prompt pronto para IA, avisos e MCP | CLOSED | 2026-11-06 | 2026-11-07 | PR 77 | documentation |
| 55 | feat: avisos configuráveis por etapa e por tipo de pendência (API) | CLOSED | 2026-11-06 | 2026-11-07 | PR 67 | feature, backend |
| 56 | feat: pop-ups, notificação do sistema, sino e tela de configurações (painel) | CLOSED | 2026-11-06 | 2026-11-07 | PR 70 | feature, frontend |
| 57 | feat: tokens de acesso pessoal para integrações | CLOSED | 2026-11-06 | 2026-11-07 | PR 69 | feature, security, backend |
| 58 | feat: servidor MCP do JobFly | CLOSED | 2026-11-06 | 2026-11-07 | PR 72 | feature, mcp |
| 59 | feat: instalação em um comando e conexão do MCP com a IA | CLOSED | 2026-11-06 | 2026-11-07 | PR 74, PR 75 | feature, devops |
| 60 | docs: README com prompt pronto para IA, avisos e MCP | CLOSED | 2026-11-06 | 2026-11-06 |  | documentation, duplicate |
| 61 | docs: nova logo do JobFly no painel, nos READMEs e no resumo | CLOSED | 2026-11-06 | 2026-11-07 | PR 62 | documentation, frontend |
| 63 | feat: painel em português, inglês e espanhol com detecção automática e novos idiomas automatizados | CLOSED | 2026-11-06 | 2026-11-07 | PR 68 | feature, frontend |
| 64 | feat: central de acessibilidade com os temas do Congo e os recursos do a11y-widget | CLOSED | 2026-11-06 | 2026-11-07 | PR 71 | accessibility, feature, frontend |
| 65 | feat: integração com o Congo Grey Parrot (plugin do bot de Discord) | CLOSED | 2026-11-06 | 2026-11-07 | PR 73 | feature, integration |
| 66 | feat: avisos pelo WhatsApp com a API oficial (WhatsApp Cloud API) | CLOSED | 2026-11-06 | 2026-11-07 |  | feature, backend, integration |
| 76 | test: verificações da release v0.3.0 (avisos reais, logo e auditoria de vazamento) | CLOSED | 2026-11-07 | 2026-11-07 | PR 78 | test, security |
| 79 | fix: botão de acessibilidade cobre o fim da página e sino aberto não atualiza | CLOSED | 2026-11-07 | 2026-11-07 | PR 80 | bug, frontend |
| 81 | chore: preparar a release v0.3.0 | CLOSED | 2026-11-07 | 2026-11-07 | PR 82 | chore |
| 83 | refactor: mudar o nome do projeto para JobDragonQuest | CLOSED | 2026-11-07 | 2026-11-07 | PR 85, PR 86 | refactor |
| 84 | docs: tirar do repositório as notas internas da auditoria do JobCopilot | CLOSED | 2026-11-07 | 2026-11-07 | PR 85 | documentation, security |
| 87 | fix: PostgreSQL local não sobe depois de mover a pasta do projeto | CLOSED | 2026-11-07 | 2026-11-07 | PR 88 | bug, devops |
| 89 | feat: espelho público com o histórico limpo | CLOSED | 2026-11-07 | 2026-11-07 | PR 90, PR 91 | feature, security |
| 92 | feat: comando /jobdragonquest só na conversa privada com o bot | CLOSED | 2026-11-07 | 2026-11-07 | PR 93 | feature, integration |
| 94 | fix: plugin do Congo não manda o aviso quando o painel está aberto | CLOSED | 2026-11-07 | 2026-11-07 | PR 95 | bug, integration |
| 96 | feat: perfil profissional completo | CLOSED | 2026-11-07 | 2026-11-07 | PR 97 | feature, backend, frontend |
| 98 | fix: testes do agendador de coleta falham no CI pela ordem dos testes | CLOSED | 2026-11-07 | 2026-11-07 | PR 99 | bug |
| 100 | chore: tirar dos arquivos versionados as referências externas da auditoria | CLOSED | 2026-11-07 | 2026-11-07 | PR 101 | chore |
| 102 | feat: filtros do perfil valem na coleta e na compatibilidade | CLOSED | 2026-11-07 | 2026-11-07 | PR 103 | enhancement |
| 104 | docs: instalação rápida no topo dos READMEs | CLOSED | 2026-11-07 | 2026-11-07 | PR 105 | documentation |
| 106 | feat: ações em lote no quadro de candidaturas | CLOSED | 2026-11-07 | 2026-11-07 | PR 107 | enhancement |
| 108 | feat: contatos de recrutadores e gestores por candidatura | CLOSED | 2026-11-07 | 2026-11-07 | PR 109 | enhancement |
| 110 | feat: vagas favoritas e busca por vagas parecidas | CLOSED | 2026-11-07 | 2026-11-07 | PR 111 | enhancement |
| 112 | feat: modo de automação por perfil e fila de revisão | CLOSED | 2026-11-07 | 2026-11-07 | PR 113 | enhancement |
| 114 | feat: cadastrar vaga a partir do link e importar candidaturas por CSV | CLOSED | 2026-11-07 | 2026-11-07 | PR 117 | enhancement |
| 115 | feat: analisar perguntas e respostas e revisar candidatura antes de enviar | CLOSED | 2026-11-07 | 2026-11-07 | PR 117 | enhancement |
| 116 | feat: extensão gratuita do navegador para salvar a vaga da página aberta | CLOSED | 2026-11-07 | 2026-11-07 | PR 117 | enhancement |
| 118 | feat: currículo e carta ajustados à vaga e ferramentas de carreira | CLOSED | 2026-11-07 | 2026-11-07 | PR 123 | enhancement |
| 119 | fix: conectar-mcp falha com o Claude Code (ordem do -e) e recusa token colado | CLOSED | 2026-11-07 | 2026-11-07 | PR 120 | bug |
| 124 | feat: documentos do perfil e atualização do currículo por PDF, Word, link e foto | CLOSED | 2026-11-07 | 2026-11-07 | PR 125 | enhancement |
| 126 | feat: preferências de acessibilidade salvas na conta | CLOSED | 2026-11-07 | 2026-11-07 | PR 127 | enhancement |
| 130 | docs(governanca): pendências de governança da avaliação técnica Week_01 | CLOSED | 2026-11-07 | 2026-11-07 | PR 132 | documentation, type:docs, priority:high, scope:docs |
| 131 | ci(qualidade): relatório de testes, análise de dependências, commits verificados, lint e formato de erro | CLOSED | 2026-11-07 | 2026-11-07 | PR 133 | test, ci, type:ci, priority:medium, scope:devops |

## Pull requests

| # | Título | Estado | Origem → destino | Aberto | Merge | Fecha |
|---|---|---|---|---|---|---|
| 20 | refactor: mover o código-fonte para src/ (#6) | MERGED | `feature/issue-06-estrutura-src` → `develop` | 2026-11-06 | 2026-11-06 | #6 |
| 21 | feat(sources): fontes vigiadas em lote para vários perfis (#9) | MERGED | `feature/issue-09-fontes-em-lote` → `develop` | 2026-11-06 | 2026-11-06 | #9 |
| 22 | feat(sources): coleta de ATS públicos com agendador (#10) | MERGED | `feature/issue-10-coletores-ats` → `develop` | 2026-11-06 | 2026-11-06 | #10 |
| 23 | feat(sources): lembretes para fontes assistidas (#11) | MERGED | `feature/issue-11-fontes-assistidas` → `develop` | 2026-11-06 | 2026-11-06 | #11 |
| 24 | feat(web): base do painel (#12) | MERGED | `feature/issue-12-base-painel` → `develop` | 2026-11-06 | 2026-11-06 | #12 |
| 25 | feat(web): telas de perfis (#13) | MERGED | `feature/issue-13-telas-perfis` → `develop` | 2026-11-06 | 2026-11-06 | #13 |
| 26 | feat(web): fontes vigiadas em lote (#14) | MERGED | `feature/issue-14-tela-fontes` → `develop` | 2026-11-06 | 2026-11-06 | #14 |
| 27 | feat(web): vagas e compatibilidade (#15) | MERGED | `feature/issue-15-vagas-compatibilidade` → `develop` | 2026-11-06 | 2026-11-06 | #15 |
| 28 | feat(web): candidaturas com Kanban (#16) | MERGED | `feature/issue-16-candidaturas-kanban` → `develop` | 2026-11-06 | 2026-11-06 | #16 |
| 29 | feat(web): central de pendências (#17) | MERGED | `feature/issue-17-central-pendencias` → `develop` | 2026-11-06 | 2026-11-06 | #17 |
| 30 | test: fumaça da API e coleta real (#19) | MERGED | `feature/issue-19-testes-fumaca` → `develop` | 2026-11-06 | 2026-11-06 | #19 |
| 33 | chore(tools): unlazy, OpenCode e verificadores dos gates (#18) | MERGED | `feature/issue-18-unlazy-opencode` → `develop` | 2026-11-06 | 2026-11-06 | #18 |
| 34 | fix(sources): impedir coletas simultâneas da mesma fonte (#31) | MERGED | `fix/issue-31-coleta-concorrente` → `develop` | 2026-11-06 | 2026-11-06 | #31 |
| 35 | fix(matching): casos de borda achados com o OpenCode (#32) | MERGED | `fix/issue-32-casos-de-borda-compatibilidade` → `develop` | 2026-11-06 | 2026-11-06 | #32 |
| 37 | fix(scripts): parar encerra todos os processos (#36) | MERGED | `fix/issue-36-parar-servicos` → `develop` | 2026-11-06 | 2026-11-06 | #36 |
| 39 | fix(web): cor da nota e pendências traduzidas (#38) | MERGED | `fix/issue-38-acabamento-painel` → `develop` | 2026-11-06 | 2026-11-06 | #38 |
| 40 | docs: termo de abertura do projeto (#1) | MERGED | `feature/issue-01-project-charter` → `develop` | 2026-11-06 | 2026-11-06 | #1 |
| 41 | docs(rf): requisitos rastreáveis (#2) | MERGED | `feature/issue-02-requisitos` → `develop` | 2026-11-06 | 2026-11-06 | #2 |
| 42 | docs: casos de uso (#3) | MERGED | `feature/issue-03-casos-de-uso` → `develop` | 2026-11-06 | 2026-11-06 | #3 |
| 43 | docs: histórias de usuário (#4) | MERGED | `feature/issue-04-user-stories` → `develop` | 2026-11-06 | 2026-11-06 | #4 |
| 44 | docs: diagramas C4, classes e DER (#5) | MERGED | `feature/issue-05-diagramas` → `develop` | 2026-11-06 | 2026-11-06 | #5 |
| 45 | ci: build e testes da API e do painel (#8) | MERGED | `feature/issue-08-ci` → `develop` | 2026-11-06 | 2026-11-06 | #8 |
| 46 | docs: README com badges, funcionalidades e fluxo de branches (#7) | MERGED | `feature/issue-07-readme` → `develop` | 2026-11-06 | 2026-11-06 | #7 |
| 48 | docs: resumo do projeto em PDF (#47) | MERGED | `feature/issue-47-resumo-pdf` → `develop` | 2026-11-06 | 2026-11-06 | #47 |
| 49 | release: v0.2.0 | MERGED | `develop` → `main` | 2026-11-06 | 2026-11-06 | #1, #2, #3, #4, #5, #6, #7, #8, #9, #10, #11, #12, #13, #14, #15, #16, #17, #18, #19, #31, #32, #36, #38, #47 |
| 51 | fix(docs): regra de crédito do AGENTS.md (#50) | MERGED | `hotfix/issue-50-agents-md` → `main` | 2026-11-06 | 2026-11-06 | #50 |
| 53 | fix(scripts): verificador de Git Flow usa a tag mais recente (#52) | MERGED | `fix/issue-52-verificador-git-flow` → `develop` | 2026-11-06 | 2026-11-06 | #52 |
| 62 | docs: nova logo do JobFly (#61) | MERGED | `feature/issue-61-nova-logo` → `develop` | 2026-11-06 | 2026-11-06 | #61 |
| 67 | feat(avisos): avisos configuráveis por etapa, tipo e fonte (#55) | MERGED | `feature/issue-55-avisos-api` → `develop` | 2026-11-07 | 2026-11-07 | #55 |
| 68 | feat(idiomas): português, inglês e espanhol com detecção automática (#63) | MERGED | `feature/issue-63-idiomas` → `develop` | 2026-11-07 | 2026-11-07 | #63 |
| 69 | feat(tokens): tokens de acesso pessoal para integrações (#57) | MERGED | `feature/issue-57-tokens` → `develop` | 2026-11-07 | 2026-11-07 | #57 |
| 70 | feat(avisos): pop-ups, notificação do sistema, sino e configurações (#56) | MERGED | `feature/issue-56-avisos-painel` → `develop` | 2026-11-07 | 2026-11-07 | #56 |
| 71 | feat(acessibilidade): temas do Congo e recursos do a11y-widget (#64) | MERGED | `feature/issue-64-acessibilidade` → `develop` | 2026-11-07 | 2026-11-07 | #64 |
| 72 | feat(mcp): servidor MCP do JobFly (#58) | MERGED | `feature/issue-58-mcp` → `develop` | 2026-11-07 | 2026-11-07 | #58 |
| 73 | feat(congo): plugin do JobFly para o Congo Grey Parrot (#65) | MERGED | `feature/issue-65-congo` → `develop` | 2026-11-07 | 2026-11-07 | #65 |
| 74 | feat(instalacao): instalação em um comando e conexão do MCP (#59) | MERGED | `feature/issue-59-instalacao` → `develop` | 2026-11-07 | 2026-11-07 | #59 |
| 75 | docs(instalacao): READMEs em inglês e espanhol com a instalação em um comando (#59) | MERGED | `fix/issue-59-readmes` → `develop` | 2026-11-07 | 2026-11-07 | #59 |
| 77 | docs(readme): prompt pronto para IA e deploy opcional gratuito (#54) | MERGED | `feature/issue-54-readme-prompt` → `develop` | 2026-11-07 | 2026-11-07 | #54 |
| 78 | test(verificacao): verificações da release v0.3.0 (#76) | MERGED | `feature/issue-76-verificacoes` → `develop` | 2026-11-07 | 2026-11-07 | #76 |
| 80 | fix(painel): botão de acessibilidade e sino aberto (#79) | MERGED | `fix/issue-79-navegacao` → `develop` | 2026-11-07 | 2026-11-07 | #79 |
| 82 | chore(release): preparar a v0.3.0 (#81) | MERGED | `feature/issue-81-release-v030` → `develop` | 2026-11-07 | 2026-11-07 | #81 |
| 85 | refactor: JobDragonQuest, com slogan, e notas internas fora do repositório (#83, #84) | MERGED | `feature/issue-83-jobdragonquest` → `develop` | 2026-11-07 | 2026-11-07 | #83, #84 |
| 86 | release: v0.3.0 | MERGED | `develop` → `main` | 2026-11-07 | 2026-11-07 | #83 |
| 88 | fix(scripts): PostgreSQL local depois de mover a pasta (#87) | MERGED | `fix/issue-87-socket-postgres` → `develop` | 2026-11-07 | 2026-11-07 | #87 |
| 90 | feat(espelho): espelho público com histórico limpo (#89) | MERGED | `feature/issue-89-espelho-publico` → `develop` | 2026-11-07 | 2026-11-07 | #89 |
| 91 | release: v0.3.1 | MERGED | `develop` → `main` | 2026-11-07 | 2026-11-07 | #89 |
| 93 | feat(congo): comando só na conversa privada com o bot (#92) | MERGED | `feature/issue-92-comando-dm` → `develop` | 2026-11-07 | 2026-11-07 | #92 |
| 95 | fix(congo): aviso por DM com o painel aberto (#94) | MERGED | `fix/issue-94-plugin-painel-aberto` → `develop` | 2026-11-07 | 2026-11-07 | #94 |
| 97 | feat(perfil): perfil completo (#96) | MERGED | `feature/issue-96-perfil-completo` → `develop` | 2026-11-07 | 2026-11-07 | #96 |
| 99 | fix(testes): agendador isolado nos testes (#98) | MERGED | `fix/issue-98-teste-agendador` → `develop` | 2026-11-07 | 2026-11-07 | #98 |
| 101 | chore: referências externas fora do Git (#100) | MERGED | `chore/issue-100-referencias` → `develop` | 2026-11-07 | 2026-11-07 | #100 |
| 103 | feat: filtros do perfil na coleta e compatibilidade (#102) | MERGED | `feature/issue-102-filtros-perfil` → `develop` | 2026-11-07 | 2026-11-07 | #102 |
| 105 | docs: instalação rápida no topo dos READMEs (#104) | MERGED | `docs/issue-104-readme-rapido` → `develop` | 2026-11-07 | 2026-11-07 | #104 |
| 107 | feat: ações em lote no quadro (#106) | MERGED | `feature/issue-106-lote` → `develop` | 2026-11-07 | 2026-11-07 | #106 |
| 109 | feat: contatos do processo por candidatura (#108) | MERGED | `feature/issue-108-contatos` → `develop` | 2026-11-07 | 2026-11-07 | #108 |
| 111 | feat: favoritas e vagas parecidas (#110) | MERGED | `feature/issue-110-favoritos` → `develop` | 2026-11-07 | 2026-11-07 | #110 |
| 113 | feat: modo de automação e fila de revisão (#112) | MERGED | `feature/issue-112-automacao` → `develop` | 2026-11-07 | 2026-11-07 | #112 |
| 117 | feat: link, CSV, perguntas e revisão, extensão (#114, #115, #116) | MERGED | `feature/issue-114-link-csv` → `develop` | 2026-11-07 | 2026-11-07 | #114, #115, #116 |
| 120 | fix: conectar-mcp com o Claude Code e token colado (#119) | MERGED | `fix/issue-119-conectar-mcp` → `develop` | 2026-11-07 | 2026-11-07 | #119 |
| 121 | hotfix: v0.3.2, conexão do MCP com o Claude Code (#119) | MERGED | `hotfix/0.3.2` → `main` | 2026-11-07 | 2026-11-07 |  |
| 122 | chore: main (v0.3.2) de volta para develop | MERGED | `chore/main-para-develop-0.3.2` → `develop` | 2026-11-07 | 2026-11-07 |  |
| 123 | feat: currículo, carta e ferramentas de carreira (#118) | MERGED | `feature/issue-118-carreira` → `develop` | 2026-11-07 | 2026-11-07 | #118 |
| 125 | feat: documentos e atualização do currículo (#124) | MERGED | `feature/issue-124-documentos` → `develop` | 2026-11-07 | 2026-11-07 | #124 |
| 127 | feat: acessibilidade salva na conta (#126) | MERGED | `feature/issue-126-acessibilidade-conta` → `develop` | 2026-11-07 | 2026-11-07 | #126 |
| 128 | release: v0.4.0 | MERGED | `release/0.4.0` → `main` | 2026-11-07 | 2026-11-07 |  |
| 129 | chore: main (v0.4.0) de volta para develop | MERGED | `chore/main-para-develop-0.4.0` → `develop` | 2026-11-07 | 2026-11-07 |  |
| 132 | docs(governanca): pendências de governança da avaliação técnica Week_01 (#130) | MERGED | `feature/issue-130-governanca` → `develop` | 2026-11-07 | 2026-11-07 | #130 |
| 133 | ci(qualidade): relatórios de teste, OSV-Scanner, commits verificados, ESLint e formato de erro (#131) | MERGED | `feature/issue-131-qualidade` → `develop` | 2026-11-07 | 2026-11-07 | #131 |
