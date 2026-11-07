# Matriz de funcionalidades: JobCopilot × JobDragonQuest

Fontes:
- páginas públicas do jobcopilot.com;
- observação da conta autorizada da usuária em 05/11/2026 (notas em `_notas_trabalho.md`).

O JobCopilot é referência funcional, não limite. Nenhum código, texto ou identidade visual foi copiado.

| Recurso | JobCopilot (observado ou anunciado) | JobDragonQuest: decisão | JobDragonQuest: situação |
|---|---|---|---|
| Busca automática de vagas | Varre páginas de carreira. Anuncia "500.000+" fontes | APIs públicas de ATS e Empresas Monitoradas. Nada de scraping proibido | ⬜ |
| Filtros do copiloto | Cargos (até 5), países, modalidade, senioridade, palavras incluídas e excluídas, empresas excluídas, salário mensal | Preferências por perfil (cargos, locais, modalidades, senioridade, nota mínima) | ✅ no perfil |
| Vários perfis | 1 copiloto (Premium) ou 3 (Elite) | Até 30 perfis independentes, sem misturar dados | ✅ |
| Compatibilidade | Nível de "match" (Higher/High/Highest) sem explicação pública | Nota de 0 a 100 com justificativa por requisito e impedimentos eliminatórios separados | ✅ |
| Automação total ou com revisão | Modo A (100% automático) e modo B (preenche e salva para revisar) | Política de autonomia: automática, condicionada ou aprovação obrigatória | ⬜ |
| Respostas de formulário | Respostas da IA por categoria. Edições da pessoa treinam o copiloto | Análise de perguntas eliminatórias e respostas só com dados reais do perfil | ⬜ |
| Currículo por vaga | Só no Elite | Versões preservadas, sem inventar experiência | ⬜ |
| Carta de apresentação | Genérica ou por vaga | Idem, com dados do perfil | ⬜ |
| Kanban | Saved, Applied, Contacted, Interviewing, Offer | 19 etapas, todas as origens, com evidência e confiança | ✅ na API |
| Candidaturas externas | Não observado: o painel mostra só o que o copiloto enviou | Qualquer origem: manual, link, e-mail, extensão, importação, rede social, indicação | ✅ manual e social. ⬜ os demais |
| Atualização automática de etapa | Não observado: a mudança de coluna é manual | Por evidência (e-mail ou portal), com sugestão quando a confiança é baixa | 🟡 (API pronta) |
| Contato com recrutadores | Créditos para "hiring managers" | Respostas por e-mail com política de autonomia. Sem compra de contatos | ⬜ |
| Entrevista simulada e ferramentas de carreira | Mock interview, salário, mudança de carreira | Fora do escopo inicial | — |
| Integração com e-mail | Não anunciada | Gmail e Outlook por OAuth com escopos mínimos e sem apagar nada | ⬜ 🔑 |
| API, MCP, exportação | Não há API pública | API REST documentada e servidor MCP | ✅ REST. ⬜ MCP |
| Transparência | Sem justificativa de decisão | Cada decisão registra origem, evidência e confiança, mais trilha de auditoria | ✅ |

Melhorias do JobDragonQuest em relação à referência:
1. central única de todas as candidaturas, mesmo as externas;
2. explicação de cada nota;
3. impedimentos eliminatórios separados da nota;
4. nenhuma inferência de reprovação por silêncio;
5. dados e regras sob controle da usuária.
