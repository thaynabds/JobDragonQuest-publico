# Changelog

Todas as mudanças relevantes do JobDragonQuest. O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/)
e as versões seguem o [SemVer 2.0.0](https://semver.org/lang/pt-BR/). Os números entre parênteses são as issues do
repositório principal; a lista completa está em [docs/historico-issues-prs.md](docs/historico-issues-prs.md).

## [Não lançado]

## [0.7.1] - 2026-11-09

### Corrigido
- A análise de perguntas da vaga ia sem o perfil e dava erro interno; parâmetro obrigatório ausente agora responde 400 (#205).

### Alterado
- Menu lateral 90% opaco com leve desfoque: o conteúdo atrás aparece sem atrapalhar a leitura (#202).

## [0.7.0] - 2026-11-09

### Adicionado
- Currículo com escolha por dado, prévia em tempo real e download em Word; vários links e campo aberto de diversidade (#190).
- Busca e filtros equivalentes em português, inglês e espanhol, com nível deduzido do título (#190).
- Assistentes de carreira executados pela IA ligada por MCP, sem custo (#192).
- Assistente que acompanha e-mails de candidatura pelo conector da própria IA, só leitura (#198).
- Acompanhamento de concursos, com leitura de páginas e PDFs oficiais (#192).
- Base de perguntas de triagem, central de ajuda, exportação CSV, exclusão de conta, filtro por idioma da vaga e preenchimento de formulário pela extensão (#192).
- Francês, italiano, chinês, japonês, hindi, árabe, bengali, russo e indonésio nas telas principais; o restante aparece em inglês (#192).

### Alterado
- O painel de acessibilidade próprio deu lugar ao AccessAbility Widget 2.3.0; as preferências ficam neste navegador (#196).

### Corrigido
- O painel de avisos não alarga mais a tela (#194).
- Método HTTP não aceito responde 405 em vez de 500 (#190).

## [0.6.2] - 2026-11-09

### Adicionado
- Laudo, diploma e histórico escolar como documentos do perfil. O laudo não tem o texto extraído e não abre por token de integração (#187).
- O login avisa quantas tentativas restam e só bloqueia depois de 5 erros (#186).

### Corrigido
- Responsividade de todas as telas, de 320 px a telas largas, com teste automático em 5 larguras (#184).
- Abrir várias páginas em seguida deslogava a pessoa (#184).
- O aviso de versão não pede mais para recarregar quando o painel já é o mais novo (#184).

## [0.6.1] - 2026-11-09

### Adicionado
- Extensão do navegador aceita servidor em HTTPS, com permissão pedida na hora (#180).

### Alterado
- Espelho público publicado por commit limpo, sem o histórico do principal (#180).

### Corrigido
- `render.yaml` sem e-mail e sem endereço do banco (#178).

## [0.6.0] - 2026-11-09

### Adicionado
- Conta: ver senha, permanecer conectado, alterar nome, e-mail e senha, e códigos de recuperação para quem esquecer a senha (#172).
- Perfil: níveis aceitos, fusos, idiomas, raio, telefones, nacionalidades, faixa salarial, nome social, diversidade, horários de contato, competências comportamentais, links extras e escolha do que aparece no currículo (#172).
- Currículo e carta no idioma da vaga, com tradução automática opcional e textos editáveis (#172).
- Tokens de acesso sem validade, frequência das fontes em horas ou dias, identificador do board com qualquer caractere (#172).
- Excluir um aviso ou todos, aviso de nova versão dentro do painel e menu recolhido que abre de qualquer página (#172).
- Leitura de currículo em itens separados e de arquivos ODT, ODS, Excel (.xlsx), CSV e Markdown, com os formatos informados na tela (#172, #174).
- Botão "i" com explicação nos campos e conversor de moedas de países de língua portuguesa, inglesa e espanhola (#174).

### Corrigido
- Word gerado por outros programas (prefixo diferente no XML) virava uma linha só, e PDF com colunas saía embaralhado (#172).
- A busca do LinkedIn salva como fonte perdia as palavras-chave, e os filtros de outros sites eram cortados (#172).

### Adicionado
- Aviso de commit grande (mais de 30 arquivos ou 800 linhas) no hook de pre-commit e nos PRs do CI, sem bloquear (#170).

## [0.5.1] - 2026-11-07

### Adicionado
- Teste ponta a ponta no navegador (Playwright): conta, perfil, candidatura, etapa com evidência e isolamento de outra
  conta, contra a API e um PostgreSQL limpos no CI (#166).
- `docs/adr/`: um arquivo por decisão de arquitetura (ADR-001 a ADR-010) (#166).

### Corrigido
- ADRs citavam react-i18next, fila com `SKIP LOCKED`, escopos de token e `traceId`, que não existem no código (#166).

## [0.5.0] - 2026-11-07

### Adicionado
- Ruff (Python) e Checkstyle (Java) no CI, hooks de pre-commit e commit-msg (`scripts/instalar-hooks.sh`) (#159).
- `X-Request-Id` em cada requisição e no log, com opção de log em JSON (#159).
- Teste ponta a ponta do fluxo coleta → análise → candidatura e medição do p95 (pior rota 103 ms, limite 300 ms) (#159).
- Página de Release criada pelo CI a partir do CHANGELOG (#159).
- `docs/runbook.md` e `docs/acessibilidade.md` (#159).

## [0.4.2] - 2026-11-07

### Alterado
- pdfbox 3.0.8 (leitura de PDF dos documentos do perfil) e SDK do MCP 1.32.2 (#150).
- Ações do CI atualizadas (checkout 7, setup-java 6, setup-node 7, setup-python 7) e fixadas por SHA (#150).
- Dependabot ignora versões major de bibliotecas, que exigem migração planejada (#151), e agrupa as menores (#150).

### Corrigido
- PRs do Dependabot deixavam vermelha a página de Actions do espelho público, onde não podem ser mergeados: o CI de
  pull_request passa a rodar só no repositório principal (#150).

## [0.4.1] - 2026-11-07

### Adicionado
- `LICENSE`, `CHANGELOG.md`, `CONTRIBUTING.md` e `SECURITY.md` (#130).
- Templates de issue (bug, funcionalidade, documentação) e de PR com checklist de testes e segurança (#130).
- Dependabot semanal para Maven, npm (painel e MCP) e GitHub Actions (#130).
- `docs/README.md` com o índice dos documentos e a regra de sincronização documentação × código (#130).
- `docs/governanca.md` explicando o espelho público e `docs/historico-issues-prs.md` com o histórico exportado (#130).
- Coluna de issue na rastreabilidade de `docs/requisitos.md` (RF → issue → teste) (#130).
- Conjunto padrão de 23 labels nos dois repositórios (#130).

- CI publica os relatórios de teste e cobertura como artefato e o total de testes no resumo de cada execução (#131).
- Análise de dependências com OSV-Scanner no SBOM CycloneDX da API (com as transitivas), no painel e no MCP (#131).
- Checagem de Conventional Commits em cada PR (`scripts/verificar/commits.mjs`, com testes) (#131).
- ESLint no painel, dentro do `npm run verify`, com regras contra `eval` e injeção de HTML (#131).
- Formato de erro da API documentado em `docs/api.md` e coberto pelo `ErrorFormatTest` (#131).

### Corrigido
- Notificação do sistema podia mostrar empresa e cargo antes de carregar a preferência "esconder detalhes"; o teste
  que cobria isso era instável e agora reproduz a corrida de forma determinística (#131).
- minimatch 10.2.6 (GHSA-g3hv-75wg-m249, negação de serviço), trazida pelo ESLint, fixada em 11.0.1 (#131).
- Teste do editor de perfil falhava com a máquina carregada: a espera depois de salvar tinha só 1 s (#131).
- 401 da segurança saía sem corpo e 413 do limite de tamanho saía fora do formato Problem Details (#131).
- Verificador de Git Flow recusava commits com várias issues e branches com número de três dígitos (#131).
- RF22, RF23 e RF25 constavam como planejados embora entregues na v0.4.0 (#130).
- Roadmap com critérios e cenários desatualizados depois da v0.4.0 (#130).

## [0.4.0] - 2026-11-07

### Adicionado
- Perfil completo: 13 senioridades, competências em anos, meses e dias, idiomas por nome (#96).
- Filtros do perfil valem na coleta e na compatibilidade (#102).
- Ações em lote no quadro de candidaturas (#106) e contatos de recrutadores e gestores (#108).
- Vagas favoritas e busca por vagas parecidas (#110).
- Modo de automação por perfil com fila de revisão (#112).
- Vaga a partir do link e importação de candidaturas por CSV (#114).
- Análise de perguntas e revisão da candidatura antes de enviar (#115).
- Extensão gratuita do navegador para salvar a vaga da página aberta (#116).
- Currículo e carta ajustados à vaga e ferramentas de carreira (#118).
- Documentos do perfil e atualização do currículo por PDF, Word, link e foto (#124).
- Preferências de acessibilidade salvas na conta (#126).

### Corrigido
- Testes do agendador de coleta dependiam da ordem de execução e falhavam no CI (#98). Era a causa do CI vermelho na
  `main` do espelho até a v0.3.2, que saiu da `main` antiga sem essa correção.

## [0.3.2] - 2026-11-07

### Corrigido
- Conexão do MCP com o Claude Code (ordem do `-e` no registro) e token colado com marcadores de colagem (#119).

## [0.3.1] - 2026-11-07

### Adicionado
- Espelho público com histórico limpo e script que audita antes de publicar (#89).

### Corrigido
- PostgreSQL local sobe mesmo depois de mover a pasta do projeto (#87).

## [0.3.0] - 2026-11-07

### Adicionado
- Avisos configuráveis com pop-up, notificação do sistema, som e sino (#55, #56).
- Tokens de acesso pessoal para integrações (#57).
- Servidor MCP (#58) e instalação em um comando com conexão do MCP (#59).
- README com prompt pronto para IA (#54) e nova logo (#61).
- Painel em português, inglês e espanhol com idiomas automatizados (#63).
- Central de acessibilidade (#64) e plugin do Congo Grey Parrot para o Discord (#65, #92, #94).

### Alterado
- O projeto passa a se chamar JobDragonQuest (#83).

## [0.2.1] - 2026-11-06

### Corrigido
- Regra de crédito do AGENTS.md disparava a própria checagem (#50).

## [0.2.0] - 2026-11-06

### Adicionado
- Fontes vigiadas em lote para vários perfis (#9), coleta de ATS públicos com agendador (#10) e lembretes de fontes
  assistidas (#11).
- Painel web completo em português e inglês (#12 a #17).
- Documentação de engenharia: charter, requisitos, casos de uso, histórias e diagramas (#1 a #5), estrutura `src/` (#6),
  README (#7) e CI (#8).
- unlazy e OpenCode no projeto (#18) e testes de fumaça e de coleta real (#19).

### Corrigido
- Coleta manual concorrente com o agendador (#31), casos de borda da compatibilidade (#32), parada dos serviços (#36)
  e acabamento do painel (#38).

## [0.1.0] - 2026-11-05

### Adicionado
- Núcleo da API: contas, perfis, vagas, compatibilidade, candidaturas de qualquer origem, Kanban, pendências e auditoria,
  rodando no PostgreSQL local.

[Não lançado]: https://github.com/thaynabds/JobDragonQuest-publico/compare/v0.6.2...develop
[0.6.2]: https://github.com/thaynabds/JobDragonQuest-publico/compare/v0.6.1...v0.6.2
[0.6.1]: https://github.com/thaynabds/JobDragonQuest-publico/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/thaynabds/JobDragonQuest-publico/compare/v0.5.1...v0.6.0
[0.5.1]: https://github.com/thaynabds/JobDragonQuest-publico/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/thaynabds/JobDragonQuest-publico/compare/v0.4.2...v0.5.0
[0.4.2]: https://github.com/thaynabds/JobDragonQuest-publico/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/thaynabds/JobDragonQuest-publico/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/thaynabds/JobDragonQuest-publico/compare/v0.3.2...v0.4.0
[0.3.2]: https://github.com/thaynabds/JobDragonQuest-publico/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/thaynabds/JobDragonQuest-publico/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/thaynabds/JobDragonQuest-publico/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/thaynabds/JobDragonQuest-publico/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/thaynabds/JobDragonQuest-publico/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/thaynabds/JobDragonQuest-publico/releases/tag/v0.1.0
