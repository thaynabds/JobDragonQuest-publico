# Requisitos do JobDragonQuest

Requisitos numerados e rastreáveis. Cada requisito aponta a issue do repositório principal que o entregou (lista completa em [historico-issues-prs.md](historico-issues-prs.md); `v0.1.0` é o núcleo anterior às issues), e aparece nos casos de uso ([casos-de-uso.md](casos-de-uso.md)), nas histórias ([user-stories.md](user-stories.md)) e nos testes.

**Situação:**
- ✅ implementado e comprovado pelo teste citado na coluna Rastreio.
- 🟡 parcial.
- ⬜ planejado.

## Requisitos funcionais (RF)

| ID | Requisito | Situação | Issue | Rastreio (caso de uso, história, teste) |
|---|---|---|---|---|
| [RF01] | O sistema deve permitir cadastrar uma conta com e-mail e senha e entrar nela, mantendo a sessão de forma segura | ✅ | v0.1.0, #12 | UC01, US01, `AuthIntegrationTest` |
| [RF02] | O sistema deve permitir cadastrar, editar e excluir vários perfis profissionais independentes por conta | ✅ | v0.1.0, #13 | UC02, US02, `ProfileIntegrationTest` |
| [RF03] | Cada perfil deve guardar objetivo, cargos desejados, competências com nível e anos, idiomas (CEFR), experiências, formação, projetos, cursos, certificações, pretensão salarial, modalidades, localidades, disponibilidade e restrições | ✅ | #96, #124 | US02, `ProfileCompleteIntegrationTest`, `DocumentTest`, `ProfileEditorPage.test.tsx` |
| [RF04] | O sistema deve permitir cadastrar vagas com origem, URL original, empresa, cargo, local, modalidade, senioridade, requisitos obrigatórios e desejáveis, idiomas e remuneração | ✅ | v0.1.0, #15, #114 | UC04, `JobAndMatchingIntegrationTest` |
| [RF05] | O sistema deve reconhecer vagas e candidaturas duplicadas (URL canônica, empresa e cargo normalizados, chave de idempotência) e devolver o registro existente | ✅ | v0.1.0, #114 | US05, US08, `JobAndMatchingIntegrationTest`, `ApplicationIntegrationTest`, `ApplicationCsvImportTest` |
| [RF06] | O sistema deve analisar a compatibilidade entre vaga e perfil com nota de 0 a 100 e justificativa por requisito | ✅ | v0.1.0, #15, #32 | UC04, US04, `MatchingEngineTest` |
| [RF07] | O sistema deve avaliar impedimentos eliminatórios (modalidade, localidade, idioma exigido, senioridade) separados da nota, e só recomendar candidatura direta com todos os obrigatórios atendidos e avaliados | ✅ | #32, #102 | US04, `MatchingEngineTest`, `PreferenceFiltersTest` |
| [RF08] | O sistema deve indicar automaticamente o perfil mais adequado para cada vaga | ✅ | #10, #15 | US04, `SourceCollectorTest` |
| [RF09] | O sistema deve permitir registrar candidaturas de qualquer origem (manual, link, e-mail, extensão, importação, rede social, indicação) | ✅ | #16, #114, #116 | UC05, US05, `ApplicationIntegrationTest`, `JobLinkTest`, `extensao-navegador/comum.test.mjs` |
| [RF10] | O sistema deve acompanhar a candidatura em 19 etapas, registrando em cada mudança a origem, a evidência e a confiança | ✅ | v0.1.0, #16 | UC06, US06, `ApplicationStateMachineTest` |
| [RF11] | O sistema deve transformar uma mudança automática de baixa confiança em sugestão para a pessoa aceitar ou descartar, sem mudar a etapa | ✅ | v0.1.0, #112 | US07, `ApplicationIntegrationTest`, `ReviewQueuePage.test.tsx` |
| [RF12] | O sistema deve indicar candidaturas sem atualização sem tratar o silêncio como reprovação | ✅ | #17, #55 | US06, `NotificationIntegrationTest`, `PendingList.test.tsx` |
| [RF13] | O sistema deve permitir cadastrar sites, ATS e redes sociais a vigiar, vários de uma vez, ligados a um ou mais perfis | ✅ | #9, #14 | UC03, US03, `SourceIntegrationTest` |
| [RF14] | O sistema deve permitir editar fontes em lote (pausar, retomar, mudar frequência, ligar e desligar perfis) | ✅ | #9, #14 | US03, `SourceIntegrationTest`, `SourcesPage.test.tsx` |
| [RF15] | O sistema deve coletar vagas automaticamente nas APIs públicas de ATS (Greenhouse, Lever, SmartRecruiters, Ashby), em segundo plano, sem duplicar e recomendando as compatíveis | ✅ | #10, #31, #98 | UC07, US09, `SourceCollectorTest`, `testar-coleta.py` |
| [RF16] | O sistema deve lembrar de conferir as fontes sem coleta automática permitida (LinkedIn, Instagram, Facebook...) e registrar a conferência | ✅ | #11 | US10, `SourceAssistedTest` |
| [RF17] | O sistema deve mostrar uma visão geral com contadores e uma central de pendências priorizadas | ✅ | #17 | UC08, US11, `PendingList.test.tsx` |
| [RF18] | O sistema deve mostrar as candidaturas em Kanban por etapa, com filtros por perfil, empresa, plataforma e país | ✅ | #16, #106 | US12, `BoardPage.test.tsx`, `ApplicationBatchTest` |
| [RF19] | O sistema deve mostrar o histórico completo de cada candidatura, com evidências, notas e sugestões | ✅ | #16, #108 | US06, `ApplicationIntegrationTest`, `ApplicationContactTest` |
| [RF20] | O sistema deve oferecer a interface em português, inglês e espanhol, detectar o idioma do navegador e permitir criar idiomas novos automaticamente | ✅ | #12, #63 | US13, `messages.test.ts`, `scripts/verificar/idiomas.mjs` |
| [RF21] | O sistema deve conectar Gmail e Outlook por OAuth com escopos mínimos, classificar mensagens e relacioná-las às candidaturas, nunca apagando e-mails | ⬜ | — | Próxima entrega (roadmap) |
| [RF22] | O sistema deve analisar perguntas de formulário potencialmente eliminatórias e sugerir respostas verdadeiras | ✅ | #115 | `AssistTest`, `AssistCard.test.tsx` |
| [RF23] | O sistema deve revisar a candidatura inteira antes do envio | ✅ | #115 | `AssistTest`, `AssistCard.test.tsx` |
| [RF24] | O sistema deve oferecer ferramentas MCP com permissões controladas | ✅ | #58, #59, #119 | ADR-007, `src/jobdragonquest-mcp`, `scripts/verificar/mcp-real.mjs` |
| [RF25] | O sistema deve importar histórico de candidaturas em CSV, com relatório de duplicatas | ✅ | #114 | `ApplicationCsvImportTest`, `LinkAndImport.test.tsx` |
| [RF26] | O sistema deve avisar a pessoa de forma perceptível (pop-up, notificação do sistema e som) sobre etapas, pendências, prazos e vagas novas, com configuração por etapa, tipo e fonte, horário de silêncio e adiamento | ✅ | #55, #56, #94 | `NotificationIntegrationTest`, `notifications.test.tsx`, `SettingsPage.test.tsx` |
| [RF27] | O sistema deve oferecer tokens de acesso pessoal revogáveis para integrações, sem expor a senha | ✅ | #57 | `PersonalTokenIntegrationTest`, `TokensSection.test.tsx` |
| [RF28] | O sistema deve ter uma central de acessibilidade com os temas e filtros de cor do Congo Grey Parrot e os recursos do a11y-widget, sem carregar script externo | ✅ | #64, #126 | `a11y.test.tsx` |

## Requisitos não funcionais (RNF)

| ID | Categoria | Requisito | Situação | Comprovação |
|---|---|---|---|---|
| [RNF01] | Segurança | Senhas com Argon2id, sessão por JWT de 15 minutos e refresh token rotativo, guardado só como hash, em cookie HttpOnly, SameSite=Strict, com detecção de reuso | ✅ | `AuthIntegrationTest`, `SecurityUnitTest` |
| [RNF02] | Segurança | Isolamento por usuário em toda consulta, e recurso de outro usuário responde 404 | ✅ | `ProfileIntegrationTest`, `SourceIntegrationTest`, `ApplicationIntegrationTest` (recurso alheio responde 404) |
| [RNF03] | Segurança | Proteção contra CSRF, XSS (links só http/https, evidências exibidas como texto, CSP), SQL injection, SSRF (coleta só para hosts fixos de ATS, sem redirecionamento) e força bruta (limite de tentativas e bloqueio) | ✅ | `SafeWebFetcherTest`, `JobLinkBlockedTest`, `ExtensionCorsTest`, `safe.test.ts` |
| [RNF04] | Segurança | Segredos só em variáveis de ambiente e arquivos ignorados pelo Git. API e painel escutam só em 127.0.0.1 por padrão | ✅ | `scripts/verificar/vazamento.mjs`, `RegistrationAllowlistTest` |
| [RNF05] | Persistência | PostgreSQL com migrações Flyway versionadas. O Hibernate só valida o esquema. Datas em UTC | ✅ | Flyway e `ddl-auto=validate` em todos os testes de integração |
| [RNF06] | Persistência | Lock otimista nos registros editáveis (perfil, candidatura, fonte) para não sobrescrever edição concorrente | ✅ | `ProfileIntegrationTest`, `SourceIntegrationTest` |
| [RNF07] | Desempenho | Leitura da API em até 300 ms no p95 com carga de um usuário. Coleta com limite de 200 vagas por execução e respostas de ATS de até 8 MiB | ✅ p95 medido: pior rota 103 ms (`/applications`), limite 300 ms | `DesempenhoP95Test`, `RequestSizeLimitFilterTest`, `SourceParsersTest` |
| [RNF08] | Usabilidade | Interface responsiva (a partir de 360 px), navegável por teclado, com mensagens de erro claras e traduzidas pelo código estável da API | ✅ | `a11y.test.tsx`, `messages.test.ts` |
| [RNF09] | Portabilidade | Execução com Java 21, Node 20.19+ e PostgreSQL 14+, sem ferramenta global além dessas. Maven Wrapper e scripts de inicialização com um comando, sem sudo | ✅ | `scripts/verificar/instalacao.mjs` |
| [RNF10] | Manutenibilidade | Cobertura de linhas de pelo menos 80% nos módulos críticos, verificada no build. TypeScript estrito no painel | ✅ | JaCoCo no `./mvnw verify` (falha abaixo de 80%), `tsc --noEmit` |
| [RNF11] | Confiabilidade | Falha de integração isolada (timeout, recuo exponencial, reserva de coleta), sem derrubar a aplicação | ✅ | `SourceCollectorTest`, `SourceCollectorRaceTest` |
| [RNF12] | Privacidade (LGPD/GDPR) | Minimização de dados, exclusão em cascata a partir da conta e auditoria sem conteúdo sensível | 🟡 exportação de dados planejada | — |

## Regras de negócio (RN)

1. **RN01 Veracidade.** Nenhum componente gera experiência, certificação, idioma ou competência que não esteja no perfil.
2. **RN02 Eliminatório separado.** Recomendação APPLY só sem impedimento eliminatório, com nota no mínimo do perfil e todos os obrigatórios atendidos e avaliados.
3. **RN03 Evidência.** Mudança de etapa exige origem, evidência e confiança. Fonte automática com confiança abaixo de 0,70 vira sugestão.
4. **RN04 Confirmação não é aprovação.** "Recebemos sua candidatura" leva no máximo a "Inscrição confirmada".
5. **RN05 Estados finais.** Aprovada, Reprovada e Encerrada só saem do estado final por ação da pessoa.
6. **RN06 Duplicidade.** Mesma URL canônica, ou mesma empresa e cargo nos últimos 60 dias, devolvem a candidatura existente.
7. **RN07 Autonomia.** Aceitar proposta, assinar contrato, assumir compromisso financeiro ou compartilhar dado sensível exigem sempre aprovação humana.
8. **RN08 Regras das plataformas.** Só APIs oficiais e caminhos permitidos. CAPTCHA, MFA e bloqueios nunca são contornados.
9. **RN09 E-mail.** Proibido apagar mensagens.
10. **RN10 Uma coleta por vez.** Uma fonte nunca é coletada por duas execuções ao mesmo tempo.
