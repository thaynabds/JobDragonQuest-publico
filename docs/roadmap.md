# Situação e roadmap

**Legenda.**
- ✅ implementado e testado automaticamente.
- 🟡 parcial (detalhe na coluna).
- 🔑 depende de autorização ou credencial externa.
- ⬜ não desenvolvido.

Nada aqui é apresentado como pronto sem teste que comprove.

## Versões

| Versão | Data | Conteúdo |
|---|---|---|
| v0.1.0 | 05/11/2026 | Núcleo da API: contas, perfis, vagas, compatibilidade, candidaturas de qualquer origem, Kanban, pendências e auditoria |
| v0.2.0 | 06/11/2026 | Fontes vigiadas em lote para vários perfis, coleta de ATS públicos com agendador, lembretes de fontes assistidas, painel web completo pt/en, documentação obrigatória, Git Flow, CI e correções achadas na navegação real e na revisão com o OpenCode (#31, #32, #36, #38) |
| v0.2.1 | 06/11/2026 | Correção da regra de crédito do AGENTS.md (#50) |
| v0.3.0 | 07/11/2026 | Avisos configuráveis com pop-up, notificação do sistema, som e sino (#55, #56); tokens de acesso pessoal (#57); servidor MCP com 30 ferramentas (#58); instalação em um comando e conexão do MCP (#59); README com prompt pronto para IA e deploy opcional gratuito (#54); nova logo (#61); painel em português, inglês e espanhol com idiomas automatizados (#63); central de acessibilidade com os temas do Congo e os recursos do a11y-widget (#64); plugin do Congo Grey Parrot para o Discord (#65) |
| v0.3.1 | 07/11/2026 | Espelho público com histórico limpo e script que audita antes de publicar (#89); PostgreSQL local resistente a mover a pasta (#87) |
| v0.3.2 | 07/11/2026 | Correção da conexão do MCP com o Claude Code (registro por `claude mcp add-json`), token colado aceito mesmo com marcadores de colagem e mensagem de sucesso só quando conecta (#119) |
| v0.4.0 | 07/11/2026 | Perfil completo (13 senioridades, competências em anos, meses e dias, idiomas por nome); filtros do perfil na coleta e na compatibilidade; ações em lote; contatos do processo; favoritas e vagas parecidas; modo de automação com fila de revisão; vaga pelo link e importação por CSV; perguntas e revisão antes de enviar; extensão do navegador; currículo e carta por vaga, entrevista, salário e competências; documentos e atualização do currículo por PDF, Word, link e foto; acessibilidade salva na conta; MCP com 30 ferramentas (#96 a #127) |
| v0.4.1 | 07/11/2026 | Pendências da avaliação técnica Week_01: licença, changelog, guia de contribuição, política de segurança, templates, Dependabot, labels, histórico de issues e PRs no espelho, rastreabilidade RF → issue → teste (#130); relatórios de teste e OSV-Scanner no CI, checagem de commits, ESLint, formato de erro único e correção de privacidade nas notificações do sistema (#131) |
| v0.4.2 | 07/11/2026 | pdfbox 3.0.8 e SDK do MCP 1.32.2; ações do CI novas fixadas por SHA; Dependabot sem versões major de bibliotecas; CI de PR só no repositório principal, para o espelho público não ficar vermelho com PRs que não podem ser mergeados (#150) |
| v0.5.0 | 07/11/2026 | Backlog restante da avaliação Week_01: Ruff, Checkstyle e hooks de pre-commit; X-Request-Id e log em JSON; teste ponta a ponta pela API; p95 medido (RNF07); release notes automáticas; runbook e checklist de acessibilidade (#159) |
| v0.5.1 | 07/11/2026 | Teste ponta a ponta no navegador (Playwright) no CI; ADRs em um arquivo por decisão (#166); setup-java 6.2.0 (#150) |
| v0.6.0 | 09/11/2026 | Conta completa e recuperação por códigos; opções de busca, candidatura e currículo no perfil; documentos no idioma da vaga; leitura de ODT, Excel e Markdown e currículo em itens; tokens sem validade; frequência livre; excluir avisos; aviso de versão; ajuda nos campos; conversor de moedas (#172, #174) |
| v0.6.1 | 09/11/2026 | render.yaml sem dados pessoais (#178); espelho por commit limpo e extensão com servidor HTTPS (#180) |
| v0.6.2 | 09/11/2026 | Responsividade de todas as telas com teste em 5 larguras; aviso de tentativas no login; navegar não desloga; laudo, diploma e histórico no perfil (#184, #186, #187) |
| v0.7.0 | 09/11/2026 | Currículo por dado com Word e prévia; assistentes por MCP; concursos; triagem; novos idiomas |
| v0.7.1 | 09/11/2026 | Menu lateral levemente translúcido |
| v0.7.2 | 10/11/2026 | Painel de avisos translúcido; links da tela de entrada sem cara de botão |
| v0.7.3 | 10/11/2026 | Cartões da visão geral clicáveis; avisos com link de ação; assistente de e-mail extrai dados completos; quadro e temas do widget legíveis |

Medição da v0.2.0:
- API: 108 testes, cobertura geral de 94,3%.
- Painel: 20 testes.
- Teste de fumaça: 26 verificações contra a API no PostgreSQL local.
- Coleta real: 246 vagas no board do GitLab, 88 relevantes gravadas, segunda coleta sem duplicar.

## Critérios de aceitação do projeto (seção 21 do pedido)

| # | Critério | Situação |
|---|---|---|
| 1 | Vários perfis profissionais | ✅ |
| 2 | Conectar um provedor de e-mail | ⬜ 🔑 v0.3.0 (OAuth Google ou Microsoft) |
| 3 | Buscar vagas automaticamente em fontes integradas | ✅ Greenhouse, Lever, SmartRecruiters e Ashby, com agendador |
| 4 | Identificar oportunidades compatíveis | ✅ |
| 5 | Selecionar o perfil correto | ✅ |
| 6 | Gerar documentos personalizados | ✅ currículo e carta ajustados à vaga, só com dados do perfil (#118) |
| 7 | Candidatura de teste em ambiente autorizado | ⬜ |
| 8 | Registrar o resultado da candidatura | ✅ |
| 9 | Identificar mensagens de processos seletivos | ⬜ 🔑 depende do item 2 |
| 10 | Atualizar o status por evidência | 🟡 API de transições com confiança e sugestões pronta. Falta a fonte automática (e-mail) |
| 11 | Identificar pendências | ✅ inclui fontes a conferir e integrações com falha |
| 12 | Preparar e enviar respostas autorizadas | ⬜ 🔑 |
| 13 | Histórico completo | ✅ |
| 14 | Atividades no painel web | ✅ |
| 15 | Tarefas em segundo plano | ✅ agendador de coleta com reserva, recuo e histórico |
| 16 | Ferramentas de IA com permissão controlada | ✅ servidor MCP próprio (30 ferramentas sem exclusão, token pessoal revogável) e OpenCode com permissões restritas |
| 17 | Recuperação de falhas de integração | ✅ recuo exponencial, reserva vencida liberada, falha nas pendências |
| 18 | Testes automatizados | ✅ |
| 19 | Instalar e executar pela documentação | ✅ `scripts/instalar.sh` em um comando, ou pelo prompt pronto para IA do README |

## Cenários adicionais (seção 29)

| # | Cenário | Situação |
|---|---|---|
| 1–4 | Registrar candidatura do LinkedIn, do site da empresa, do Instagram e de grupo do Facebook | ✅ |
| 5 | Adicionar por link | ✅ a página é lida com proteção contra SSRF e a vaga é extraída (#114) |
| 6 | Por encaminhamento de e-mail | ⬜ 🔑 |
| 7 | Por extensão de navegador | ✅ extensão gratuita salva a vaga da página aberta (#116) |
| 8 | Importar histórico | ✅ importação por CSV com relatório de duplicatas (#114) |
| 9–10 | Relacionar e-mail a candidatura e atualizar etapa | 🟡 A atualização por evidência está pronta |
| 11 | Monitorar portais de empresas | ✅ por ATS público (fontes vigiadas). Páginas sem ATS ficam como fontes assistidas |
| 12 | Vagas em publicações e imagens | 🟡 fontes assistidas com lembrete. A extração por IA está pendente |
| 13–16 | Perguntas eliminatórias e revisão da candidatura | ✅ análise de perguntas com respostas só a partir do perfil e revisão antes de enviar (#115) |
| 17 | Impedir duplicadas | ✅ |
| 18 | Sem informação suficiente, sem mudança | ✅ |
| 19 | Todas as candidaturas num só painel | ✅ |

## Próximas entregas

1. **E-mail (RF21).** Gmail e Outlook por OAuth com escopos mínimos, classificação de mensagens, vínculo com a candidatura (com confirmação quando houver dúvida) e marcadores. Nunca apagar. Destrava os critérios 2, 9, 10 e 12 e os cenários 6, 9 e 10.
2. **Camada de IA opcional** (`LlmProvider`: Claude, OpenAI ou local) para extrair vagas de publicações e imagens (cenário 12), sempre sem inventar dado do perfil.
3. **Operação:** Docker Compose, Redis para o limite de tentativas distribuído e OpenTelemetry (logs estruturados e p95 do [RNF07] entregues na v0.5.0).
4. **Privacidade:** exportação dos dados da conta ([RNF12], LGPD).
