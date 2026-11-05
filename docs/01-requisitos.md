# 01. Requisitos do JobFly

Plataforma de automação de candidaturas e gestão de carreira. Este documento consolida o prompt do projeto (05/11/2026) em requisitos rastreáveis. Cada requisito tem um identificador usado nos testes, nas issues e no roadmap.

Legenda de prioridade: **M** = obrigatório para o MVP, **S** = segunda entrega, **C** = desejável.

## 1. Requisitos funcionais

### RF-01 Identidade e acesso
| ID | Requisito | Prior. |
|---|---|---|
| RF-01.1 | Cadastro e login com e-mail e senha (hash Argon2id). | M |
| RF-01.2 | Token de acesso JWT de curta duração e refresh token rotativo, revogável. | M |
| RF-01.3 | Papéis `USER` e `ADMIN`. Todo dado pertence a um usuário e só ele o vê. | M |
| RF-01.4 | Login com OpenID Connect (Google, Microsoft). | S |

### RF-02 Perfis profissionais
| ID | Requisito | Prior. |
|---|---|---|
| RF-02.1 | CRUD de vários perfis por usuário (ex.: "Desenvolvedora Java", "Estágio"). | M |
| RF-02.2 | O perfil guarda objetivo, cargos desejados, competências com nível, idiomas com nível, experiências, formação, projetos, certificações, pretensão salarial, modalidades, localidades, disponibilidade e restrições. | M |
| RF-02.3 | Documentos por perfil (currículo principal, alternativos, carta padrão), versionados. Uma versão nunca é sobrescrita. | M |
| RF-02.4 | Os dados de um perfil nunca se misturam com os de outro, nem na análise nem na geração de documentos. | M |
| RF-02.5 | Histórico de alterações do perfil (auditoria). | M |

### RF-03 Vagas
| ID | Requisito | Prior. |
|---|---|---|
| RF-03.1 | Registrar a vaga com fonte, URL original, empresa, cargo, localização, modalidade, senioridade, requisitos obrigatórios e desejáveis, idiomas, remuneração e data em que foi encontrada. | M |
| RF-03.2 | Deduplicar por URL canônica e por (empresa, cargo, localização) normalizados. | M |
| RF-03.3 | Estado da vaga: `OPEN`, `CLOSED`, `UNKNOWN`. Verificação periódica de disponibilidade. | S |
| RF-03.4 | Coletores por fonte (Greenhouse, Lever, SmartRecruiters, Ashby e outras) com frequência, filtros e limites configuráveis. | S |
| RF-03.5 | Empresas Monitoradas: lista de empresas, páginas de carreira, cargos, países, frequência e pausa. | S |
| RF-03.6 | Vagas de redes sociais e imagens: extração por IA a partir de link, texto colado ou imagem, com sinais de fraude. | S |

### RF-04 Compatibilidade
| ID | Requisito | Prior. |
|---|---|---|
| RF-04.1 | Pontuação de 0 a 100 com justificativa: requisitos atendidos, parciais e não atendidos, pontos fortes, impedimentos e recomendação. | M |
| RF-04.2 | Requisitos eliminatórios (modalidade, localização, idioma obrigatório, autorização de trabalho, senioridade) são avaliados à parte. Uma nota alta não anula um impedimento. | M |
| RF-04.3 | Escolher automaticamente o perfil mais compatível com a vaga. | M |
| RF-04.4 | Limite mínimo de compatibilidade para candidatura automática, configurável por perfil. | M |
| RF-04.5 | Enriquecimento semântico por LLM (sinônimos, inferência de nível), sempre com a nota determinística como base auditável. | S |

### RF-05 Candidaturas (central unificada)
| ID | Requisito | Prior. |
|---|---|---|
| RF-05.1 | Registrar candidaturas de qualquer origem: automação, manual, link, e-mail encaminhado, extensão de navegador, importação CSV e rede social. | M |
| RF-05.2 | Impedir duplicidade por chave natural e por `Idempotency-Key`. | M |
| RF-05.3 | Fluxo de 20 estados com transições permitidas e evento de mudança com origem, evidência, data e confiança. | M |
| RF-05.4 | Sem evidência suficiente, a candidatura mantém o estado e fica marcada como "sem atualização". O silêncio nunca é tratado como reprovação. | M |
| RF-05.5 | Anexos (comprovantes, PDFs, capturas). | S |
| RF-05.6 | Importação de histórico (CSV) com relatório de duplicatas. | S |
| RF-05.7 | Automação de envio em ambiente autorizado, respeitando a política de autonomia. | C |

### RF-06 E-mail
| ID | Requisito | Prior. |
|---|---|---|
| RF-06.1 | Conectar Gmail e Outlook por OAuth 2.0 com escopos mínimos. Nenhuma senha é armazenada. | S |
| RF-06.2 | Classificar mensagens (confirmação, entrevista, teste, reprovação, proposta etc.) e relacioná-las a uma candidatura existente. Em caso de dúvida, pedir confirmação. | S |
| RF-06.3 | Redigir respostas e enviar conforme a política de autonomia (automática, condicionada ou com aprovação obrigatória). | S |
| RF-06.4 | Nunca apagar e-mails. Só marcadores e arquivamento. | M |

### RF-07 Painel web
| ID | Requisito | Prior. |
|---|---|---|
| RF-07.1 | Visão geral com contadores. | M |
| RF-07.2 | Kanban por etapa. | M |
| RF-07.3 | Página da candidatura com histórico completo e evidências. | M |
| RF-07.4 | Central de pendências. | M |
| RF-07.5 | Filtros por perfil, empresa, país, cargo, plataforma, etapa e período. | M |
| RF-07.6 | Português e inglês desde a primeira versão. | M |
| RF-07.7 | Atualização em tempo real (SSE). | S |

### RF-08 IA, agentes e MCP
| ID | Requisito | Prior. |
|---|---|---|
| RF-08.1 | Camada de abstração de LLM (Claude, OpenAI, local). O sistema funciona sem ela, em modo degradado. | S |
| RF-08.2 | Agentes especializados sob um supervisor, com ferramentas por permissão, limites e registro de decisão. | S |
| RF-08.3 | Servidor MCP com autenticação, escopos, validação e limites. | S |
| RF-08.4 | Conteúdo externo (e-mail, página, documento) é dado não confiável e nunca vira instrução (defesa contra prompt injection). | M |
| RF-08.5 | "Analisar perguntas e respostas para esta vaga": classifica perguntas eliminatórias e sugere respostas verdadeiras. | S |
| RF-08.6 | "Revisar candidatura antes de enviar". | S |

### RF-09 Automação contínua
| ID | Requisito | Prior. |
|---|---|---|
| RF-09.1 | Agendador de tarefas em segundo plano: pausar, retomar, cancelar e consultar histórico. | S |
| RF-09.2 | Reprocessamento com backoff e aviso de integração desconectada. | S |

## 2. Requisitos não funcionais

| ID | Requisito |
|---|---|
| RNF-01 | Segurança por padrão: OWASP ASVS nível 2 como referência, segredos fora do código, logs sem dados sensíveis. |
| RNF-02 | Isolamento por usuário em todas as consultas. Acesso a recurso de outro usuário responde 404, sem revelar que ele existe. |
| RNF-03 | Cobertura ≥ 80% (linhas) nos pacotes críticos, verificada no build (JaCoCo). |
| RNF-04 | Respostas de API em até 300 ms no p95 para leitura, com carga de um usuário. |
| RNF-05 | Logs estruturados (JSON), métricas (Micrometer/Prometheus), health checks (Actuator). |
| RNF-06 | Falha de integração externa isolada (timeout, retry com backoff, circuit breaker). |
| RNF-07 | LGPD/GDPR: exportação e exclusão dos dados do usuário, retenção configurável, minimização. |
| RNF-08 | i18n de mensagens (pt-BR, en), datas em UTC no banco e fuso do usuário na interface. |
| RNF-09 | Reprodutível: build por Maven Wrapper e npm, sem dependência de ferramenta global. |

## 3. Regras de negócio

1. **RN-01 Veracidade.** Nenhum componente gera experiência, certificação, idioma ou competência que não esteja no perfil. Documentos gerados citam só dados do perfil escolhido.
2. **RN-02 Eliminatório separado.** A recomendação é `APPLY` só se não houver impedimento eliminatório e a nota atingir o mínimo do perfil.
3. **RN-03 Evidência.** Uma mudança de estado precisa de origem (`MANUAL`, `EMAIL`, `PORTAL`, `SYSTEM`, `AGENT`), evidência textual e confiança (0–1). Mudança feita por agente com confiança abaixo de 0,7 vira sugestão pendente, não mudança de estado.
4. **RN-04 Confirmação não é aprovação.** "Recebemos sua candidatura" leva no máximo a `CONFIRMED`.
5. **RN-05 Estados finais.** `APPROVED`, `REJECTED` e `CLOSED` só saem do estado final por ação manual (reabertura).
6. **RN-06 Duplicidade.** Há duplicata quando a URL canônica coincide ou quando empresa e cargo normalizados coincidem dentro de 60 dias. Uma duplicata devolve a candidatura existente (HTTP 200 e cabeçalho `X-Duplicate-Of`).
7. **RN-07 Autonomia.** Aceitar proposta, assinar contrato, assumir compromisso financeiro, alterar remuneração, fazer declaração legal ou compartilhar dado sensível exigem sempre aprovação humana.
8. **RN-08 Segurança externa.** CAPTCHA, MFA e bloqueios nunca são contornados. A tarefa vira pendência com link e instrução.
9. **RN-09 E-mail.** Proibido apagar mensagens.

## 4. Critérios de aceitação e situação

A situação de cada critério das seções 21 e 29 do prompt está em [07-roadmap.md](07-roadmap.md), separada em implementado, testado, dependente de autorização externa e não desenvolvido.
