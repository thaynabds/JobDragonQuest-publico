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
| v0.3.0 | 07/11/2026 | Avisos configuráveis com pop-up, notificação do sistema, som e sino (#55, #56); tokens de acesso pessoal (#57); servidor MCP com 23 ferramentas (#58); instalação em um comando e conexão do MCP (#59); README com prompt pronto para IA e deploy opcional gratuito (#54); nova logo (#61); painel em português, inglês e espanhol com idiomas automatizados (#63); central de acessibilidade com os temas do Congo e os recursos do a11y-widget (#64); plugin do Congo Grey Parrot para o Discord (#65) |
| v0.3.2 | 07/11/2026 | Correção da conexão do MCP com o Claude Code (registro por `claude mcp add-json`), token colado aceito mesmo com marcadores de colagem e mensagem de sucesso só quando conecta (#119) |

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
| 6 | Gerar documentos personalizados | ⬜ Agentes de IA |
| 7 | Candidatura de teste em ambiente autorizado | ⬜ |
| 8 | Registrar o resultado da candidatura | ✅ |
| 9 | Identificar mensagens de processos seletivos | ⬜ 🔑 depende do item 2 |
| 10 | Atualizar o status por evidência | 🟡 API de transições com confiança e sugestões pronta. Falta a fonte automática (e-mail) |
| 11 | Identificar pendências | ✅ inclui fontes a conferir e integrações com falha |
| 12 | Preparar e enviar respostas autorizadas | ⬜ 🔑 |
| 13 | Histórico completo | ✅ |
| 14 | Atividades no painel web | ✅ |
| 15 | Tarefas em segundo plano | ✅ agendador de coleta com reserva, recuo e histórico |
| 16 | Ferramentas de IA com permissão controlada | ✅ servidor MCP próprio (23 ferramentas sem exclusão, token pessoal revogável) e OpenCode com permissões restritas |
| 17 | Recuperação de falhas de integração | ✅ recuo exponencial, reserva vencida liberada, falha nas pendências |
| 18 | Testes automatizados | ✅ |
| 19 | Instalar e executar pela documentação | ✅ `scripts/instalar.sh` em um comando, ou pelo prompt pronto para IA do README |

## Cenários adicionais (seção 29)

| # | Cenário | Situação |
|---|---|---|
| 1–4 | Registrar candidatura do LinkedIn, do site da empresa, do Instagram e de grupo do Facebook | ✅ |
| 5 | Adicionar por link | 🟡 URL validada, canonicalizada e deduplicada. Falta extrair a página (exige proteção contra SSRF) |
| 6 | Por encaminhamento de e-mail | ⬜ 🔑 |
| 7 | Por extensão de navegador | ⬜ A API já aceita `origin=BROWSER_EXTENSION` com `Idempotency-Key` |
| 8 | Importar histórico | ⬜ |
| 9–10 | Relacionar e-mail a candidatura e atualizar etapa | 🟡 A atualização por evidência está pronta |
| 11 | Monitorar portais de empresas | ✅ por ATS público (fontes vigiadas). Páginas sem ATS ficam como fontes assistidas |
| 12 | Vagas em publicações e imagens | 🟡 fontes assistidas com lembrete. A extração por IA está pendente |
| 13–16 | Perguntas eliminatórias e revisão da candidatura | ⬜ Agentes de IA |
| 17 | Impedir duplicadas | ✅ |
| 18 | Sem informação suficiente, sem mudança | ✅ |
| 19 | Todas as candidaturas num só painel | ✅ |

## Próximas entregas

1. **v0.3.0: Gmail.** OAuth com escopos mínimos, classificação de mensagens, vínculo com a candidatura (com confirmação quando houver dúvida) e marcadores. Nunca apagar.
2. **Camada de IA** (`LlmProvider`: Claude, OpenAI ou local): perguntas eliminatórias, revisão da candidatura, documentos só com dados reais, extração de vagas de publicações.
3. **Servidor MCP** do JobDragonQuest com token de escopo restrito.
4. **Extensão de navegador** "Enviar ao JobDragonQuest".
5. **Documentos versionados**, anexos e importação CSV.
6. **Operação:** Docker Compose, Redis para o limite de tentativas distribuído, OpenTelemetry, medição do p95 ([RNF07]), exportação de dados (LGPD).
