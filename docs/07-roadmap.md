# 07. Situação e roadmap

**Legenda.**
- ✅ implementado e testado automaticamente.
- 🟡 parcial (detalhe na coluna).
- 🔑 depende de autorização ou credencial externa.
- ⬜ não desenvolvido.

Nada aqui é apresentado como pronto sem teste que comprove.

## Incremento 1 (05/11/2026): núcleo da API

- **Entregue:** autenticação, perfis, vagas, compatibilidade, candidaturas de qualquer origem, máquina de estados com evidências, Kanban, visão geral, pendências e auditoria.
- **Testes:** 81 automatizados (unitários e de integração com a aplicação completa), todos passando.
- **Cobertura:** ≥ 92% das linhas em cada pacote crítico (o build falha abaixo de 80%).

## Critérios de aceitação (seção 21)

| # | Critério | Situação |
|---|---|---|
| 1 | Vários perfis profissionais | ✅ |
| 2 | Conectar um provedor de e-mail | ⬜ 🔑 Projeto feito (04-integracoes): OAuth do Google ou da Microsoft |
| 3 | Buscar vagas automaticamente em fontes integradas | ⬜ Próximo incremento: coletores Greenhouse, Lever, SmartRecruiters e Ashby (APIs públicas) |
| 4 | Identificar oportunidades compatíveis | ✅ Nota, impedimentos e recomendação |
| 5 | Selecionar o perfil correto | ✅ `GET /jobs/{id}/matches` |
| 6 | Gerar documentos personalizados | ⬜ Depende do provedor de IA e do armazenamento de arquivos |
| 7 | Candidatura de teste em ambiente autorizado | ⬜ |
| 8 | Registrar o resultado da candidatura | ✅ Eventos com evidência |
| 9 | Identificar mensagens de processos seletivos | ⬜ 🔑 Depende do item 2 |
| 10 | Atualizar o status por evidência | 🟡 A API de transições com confiança e sugestões está pronta e testada. Falta a fonte automática (e-mail) |
| 11 | Identificar pendências | ✅ |
| 12 | Preparar e enviar respostas autorizadas | ⬜ 🔑 |
| 13 | Histórico completo | ✅ `GET /applications/{id}` |
| 14 | Atividades no painel web | ⬜ A API do painel está pronta. Falta a interface React (próximo incremento) |
| 15 | Tarefas em segundo plano | ⬜ Fila em banco prevista (ADR-004) |
| 16 | Ferramentas de IA com permissão controlada | ⬜ MCP previsto (ADR-007) |
| 17 | Recuperação de falhas de integração | ⬜ Junto com as integrações |
| 18 | Testes automatizados | ✅ `./mvnw verify` |
| 19 | Instalar e executar pela documentação | 🟡 Executa nos testes com H2. No PostgreSQL local, falta você rodar `scripts/configurar-local.sh` (pede sudo) |

## Cenários adicionais (seção 29)

| # | Cenário | Situação |
|---|---|---|
| 1–4 | Registrar candidatura do LinkedIn, do site da empresa, do Instagram e de grupo do Facebook | ✅ `registersExternalApplicationsFromAnyOrigin` |
| 5 | Adicionar por link | 🟡 A URL é validada, canonicalizada e deduplicada. A extração automática da página (com proteção contra SSRF) está pendente |
| 6 | Por encaminhamento de e-mail | ⬜ 🔑 |
| 7 | Por extensão de navegador | ⬜ A API já aceita `origin=BROWSER_EXTENSION` com `Idempotency-Key` |
| 8 | Importar histórico | ⬜ CSV no próximo incremento |
| 9–10 | Relacionar e-mail a candidatura e atualizar etapa | 🟡 A atualização por evidência está pronta. Falta a leitura do e-mail |
| 11 | Monitorar portais de empresas | ⬜ |
| 12 | Vagas em publicações e imagens | ⬜ Depende da IA |
| 13–15 | Perguntas eliminatórias, riscos e respostas verdadeiras | ⬜ Depende da IA |
| 16 | Revisar candidatura antes do envio | ⬜ Depende da IA |
| 17 | Impedir duplicadas | ✅ URL canônica, empresa e cargo, `Idempotency-Key` |
| 18 | Sem informação suficiente, sem mudança de etapa | ✅ `stale` calculado, sugestões de baixa confiança, eventos `NOTED` |
| 19 | Todas as candidaturas num só painel | ✅ na API (`/applications`, `/board`, `/dashboard`) |

## Próximos incrementos (ordem recomendada)

1. **Painel web** (React + Vite + TypeScript, pt-BR/en): login, visão geral, Kanban, detalhe com histórico, pendências, perfis e cadastro de candidatura externa.
2. **Coletores de vagas** com APIs públicas (Greenhouse, Lever, SmartRecruiters, Ashby), Empresas Monitoradas, fila em banco com retentativa e backoff, e agendador.
3. **Gmail** (OAuth, escopos mínimos): classificação de mensagens, vínculo com a candidatura (com confirmação quando houver dúvida), marcadores, nunca apagar.
4. **Camada de IA** (`LlmProvider`: Claude, OpenAI ou local) e agentes: extração de requisitos, perguntas eliminatórias, revisão da candidatura, documentos personalizados que só usam dados do perfil.
5. **Servidor MCP** `jobfly-mcp` com token de escopo restrito.
6. **Extensão de navegador** (Manifest V3): "Enviar ao JobFly" em vaga ou confirmação.
7. **Documentos versionados** (currículos e cartas), anexos e importação CSV.
8. **Operação:** Docker Compose, Redis para o limite de tentativas distribuído, rastreamento distribuído (OpenTelemetry), expurgo da auditoria, exportação e exclusão de dados (LGPD).
