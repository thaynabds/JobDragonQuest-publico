# Termo de Abertura do Projeto (Project Charter): JobDragonQuest

| Item | Valor |
|---|---|
| Projeto | JobDragonQuest: plataforma de gestão de carreira e automação de candidaturas |
| Responsável | Thayná Batista da Silva |
| Repositório | https://github.com/thaynabds/JobDragonQuest-publico (privado) |
| Data de abertura | 05/11/2026 |
| Versão deste documento | 1.1 (06/11/2026, entrega v0.2.0) |

## 1. Justificativa

Quem procura emprego ou estágio na área de tecnologia se candidata em muitos lugares ao mesmo tempo: LinkedIn, Gupy, Greenhouse, sites de empresas, publicações no Instagram, grupos do Facebook, e-mail e indicações. Cada canal tem a própria tela, os próprios e-mails e o próprio jeito de avisar (ou não) o andamento.

Na prática, isso gera três problemas:
- **Perda de oportunidades.** Vagas compatíveis passam despercebidas porque ninguém consegue vigiar dezenas de páginas todos os dias.
- **Perda de controle.** A pessoa não sabe em que etapa está cada processo, quais testes estão pendentes e quais e-mails esperam resposta.
- **Candidaturas fracas.** Sem uma análise clara dos requisitos, a pessoa se candidata a vagas incompatíveis ou deixa de destacar o que tem de relevante.

Ferramentas como o JobCopilot automatizam parte do envio, mas acompanham só o que elas mesmas enviaram, decidem sem explicar e não respeitam os limites de plataformas que proíbem automação (estudo em [referencias-jobcopilot/matriz-funcionalidades.md](referencias-jobcopilot/matriz-funcionalidades.md)).

## 2. Visão do produto

> Uma central única, explicável e segura, que encontra oportunidades, analisa a compatibilidade com cada perfil profissional e acompanha todas as candidaturas, de qualquer origem, com intervenção humana só quando ela é necessária.

Princípios: veracidade (nunca inventar dados do perfil), respeito às regras das plataformas, transparência das decisões (cada nota e cada mudança de etapa têm motivo e evidência) e privacidade por padrão.

## 3. Objetivos SMART

| # | Objetivo | Indicador | Prazo |
|---|---|---|---|
| O1 | Registrar candidaturas de qualquer origem (manual, link, e-mail, extensão, importação, rede social, indicação) numa central única, sem duplicatas | 100% dos cenários 29.1–29.4 e 29.17 do backlog cobertos por testes automatizados | v0.2.0, 06/11/2026 |
| O2 | Analisar a compatibilidade vaga × perfil com justificativa por requisito e impedimentos eliminatórios separados da nota | Motor determinístico com pelo menos 15 casos de teste, incluindo os casos de borda da revisão (#32) | v0.2.0, 06/11/2026 |
| O3 | Vigiar fontes de vagas, várias de uma vez e ligadas a vários perfis, com coleta automática nas APIs públicas de ATS | Coleta real de um board público com pelo menos 50 vagas, sem duplicar na segunda execução | v0.2.0, 06/11/2026 |
| O4 | Entregar um painel web em português e inglês que mostre tudo o que depende da pessoa | Navegação real validada de ponta a ponta (cadastro, perfis, fontes, vagas, candidaturas, Kanban, pendências) | v0.2.0, 06/11/2026 |
| O5 | Manter qualidade e segurança verificáveis | Build falha com cobertura abaixo de 80% nos módulos críticos; 0 vulnerabilidades conhecidas nas dependências de produção | Contínuo, a partir da v0.1.0 |
| O6 | Integrar Gmail por OAuth com escopos mínimos e classificar mensagens de processos seletivos | Vínculo automático e-mail → candidatura com confirmação humana quando houver dúvida | v0.3.0, 04/12/2026 |

## 4. Escopo preliminar

### Dentro do escopo desta entrega (v0.2.0)

- Contas com autenticação segura (Argon2id, JWT de 15 minutos, refresh token rotativo com detecção de reuso).
- Vários perfis profissionais independentes.
- Cadastro de vagas com deduplicação por URL canônica e por empresa, cargo e local.
- Análise de compatibilidade explicável e escolha do perfil mais adequado.
- Central de candidaturas de qualquer origem, com 19 etapas, evidências, sugestões de baixa confiança e idempotência.
- Fontes vigiadas: cadastro e edição em lote, ligadas a vários perfis. Coleta automática pelas APIs públicas do Greenhouse, Lever, SmartRecruiters e Ashby, com agendador. Lembretes de conferência para plataformas sem coleta permitida.
- Painel web completo em pt-BR e en, com visão geral, Kanban, detalhe, pendências, perfis, fontes e vagas.
- Execução local com um comando (PostgreSQL do próprio usuário, sem sudo), testes automatizados, Git Flow e documentação.

### Fora do escopo desta entrega (expressamente excluído)

- Integração com Gmail e Outlook (leitura, classificação e respostas). Planejada para a v0.3.0.
- Agentes de IA (análise de perguntas eliminatórias, revisão da candidatura e documentos personalizados).
- Servidor MCP próprio do JobDragonQuest e extensão de navegador.
- Envio automático de formulários de candidatura.
- Coleta automática em LinkedIn, Indeed, Glassdoor, Instagram e Facebook, proibida pelos termos de uso dessas plataformas. Elas ficam como fontes assistidas.
- Implantação em nuvem, aplicativo móvel e múltiplos idiomas além de pt-BR e en.

## 5. Stakeholders

| Stakeholder | Papel | Interesse |
|---|---|---|
| Thayná Batista da Silva | Dona do produto, desenvolvedora e primeira usuária | Centralizar a própria busca de emprego e estágio em TI e ter um projeto de portfólio de nível profissional |
| Pessoas candidatas em TI | Usuárias finais | Não perder oportunidades e saber o que fazer em cada processo |
| Recrutadoras e recrutadores | Afetados indiretamente | Receber candidaturas compatíveis, verdadeiras e bem apresentadas |
| Plataformas de vagas e ATS | Fornecedores de dados | Uso dentro dos termos e das APIs oficiais |
| Faculdade Senac PE | Contexto acadêmico | Projeto que aplica engenharia de software com documentação, backlog e Git Flow |

## 6. Público-alvo

- Estudantes e pessoas em início de carreira em tecnologia (estágio, júnior), no Brasil e em vagas remotas internacionais.
- Pessoas em transição de carreira que mantêm perfis distintos (por exemplo: back-end Java, .NET, dados).
- Quem se candidata a muitas vagas por canais diferentes e precisa de uma visão única do andamento.

## 7. Riscos iniciais

| Risco | Mitigação |
|---|---|
| Plataformas proibirem ou mudarem APIs | Só APIs públicas documentadas. Adaptadores isolados por ATS. Fontes assistidas para o restante |
| Vazamento de dados pessoais | Isolamento por usuário, segredos fora do código, serviços só em 127.0.0.1, revisão de segurança em toda entrega |
| Recomendação errada levar a candidaturas ruins | Motor explicável, impedimentos separados da nota, APPLY só com todos os obrigatórios avaliados |
| Escopo grande para uma pessoa | Entregas incrementais com tags SemVer e backlog priorizado |

## 8. Critérios de sucesso desta entrega

Todos os gates do ledger de verificação cumpridos com evidência: testes do backend e do painel, teste de fumaça contra a API real, coleta real, painel no ar e repositório publicado com Git Flow. O resumo está no [README](../README.md) e o andamento em [roadmap.md](roadmap.md).
