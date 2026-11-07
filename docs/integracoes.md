# 04. Integrações: o que é possível e como

Regra do projeto: só usar API oficial ou mecanismo permitido pelos termos de uso. CAPTCHA, MFA e bloqueios nunca são contornados. Onde não há caminho automático legítimo, a entrada é feita pela pessoa: link, extensão de navegador, e-mail ou importação.

**Legenda.**
- ✅ = documentado publicamente pelo fornecedor.
- ⚠️ = possível, com restrição forte.
- ❌ = sem caminho automático permitido.

Esta é a pesquisa de viabilidade de 05/11/2026. Nenhuma destas integrações está implementada neste incremento (ver [roadmap.md](roadmap.md)). Antes de implementar cada uma, é preciso reler a documentação e os termos vigentes.

## Fontes de vagas

| Fonte | Situação | Caminho | Precisa de |
|---|---|---|---|
| Greenhouse | ✅ | Job Board API pública (`boards-api.greenhouse.io/v1/boards/{empresa}/jobs`), só leitura das vagas publicadas | Nome do board da empresa |
| Lever | ✅ | Postings API pública (`api.lever.co/v0/postings/{empresa}`) | Nome da empresa no Lever |
| SmartRecruiters | ✅ | Posting API pública (`api.smartrecruiters.com/v1/companies/{empresa}/postings`) | Identificador da empresa |
| Ashby | ✅ | Posting API pública (`api.ashbyhq.com/posting-api/job-board/{empresa}`) | Nome do board |
| Workday | ⚠️ | Não há API pública para candidatos. As páginas de carreira usam endpoints internos não documentados, que podem mudar sem aviso | Decisão caso a caso. Começar por link e extensão |
| Gupy | ⚠️ | A API documentada é para empresas clientes, com token da empresa. Para candidatos: link, e-mail de confirmação e extensão | — |
| LinkedIn | ❌ automático | As APIs de vagas são restritas a parceiros, e o contrato de usuário proíbe scraping e automação. Entradas: link (a URL é canonicalizada), alertas por e-mail, extensão | — |
| Indeed | ❌ automático | A API de busca para publishers foi descontinuada, e os termos proíbem scraping. Entradas: alertas por e-mail, link | — |
| Glassdoor | ❌ automático | API fechada para novos parceiros. Entrada: link | — |
| Páginas de carreira (Amazon, Avanade, CI&T, Accenture...) | ⚠️ | Primeiro identificar o ATS da empresa (muitas usam Greenhouse, Lever, Workday ou SmartRecruiters) e usar a API pública dele. Sem ATS conhecido: monitorar a página respeitando o `robots.txt`, com frequência baixa | Cadastro em "Empresas Monitoradas" |

## Redes sociais

| Rede | Situação | Detalhe |
|---|---|---|
| Instagram | ⚠️ | A Graph API faz busca por hashtag só com conta Business ou Creator, app aprovado na revisão da Meta e limite de 30 hashtags distintas em 7 dias. Alternativa sem restrição: colar link, texto ou imagem da publicação, com extração por IA e sinais de fraude |
| Facebook (grupos) | ❌ automático | A Meta descontinuou a Groups API em 2024. Entradas: link, texto ou imagem colados, extensão |
| LinkedIn (publicações) | ❌ automático | Mesma restrição das vagas. Entradas: link e extensão (a pessoa clica em "Enviar ao JobFly" na publicação aberta) |

## E-mail e agenda

| Serviço | Situação | Escopos mínimos | Observações |
|---|---|---|---|
| Gmail | ✅ | `gmail.readonly` (ler), `gmail.labels` (marcadores), `gmail.send` (respostas autorizadas) | `gmail.modify` só se for preciso arquivar. **Nunca** usar o escopo `https://mail.google.com/` (permite apagar). Escopos restritos exigem verificação do Google para publicar. Em modo de teste (até 100 usuários de teste), o refresh token expira em 7 dias |
| Outlook / Microsoft 365 | ✅ | `Mail.Read`, `Mail.Send` (delegados), `offline_access` | `Mail.ReadWrite` só para mover ou categorizar. Funciona com conta pessoal e corporativa (Microsoft Entra) |
| Google Calendar | ✅ | `calendar.events` | Entrevistas e prazos |
| Microsoft Calendar | ✅ | `Calendars.ReadWrite` | Idem |

Os tokens OAuth serão guardados cifrados (AES-GCM, chave fora do banco). Senha de e-mail nunca é pedida.

## IA

| Provedor | Situação | Precisa de |
|---|---|---|
| Claude (Anthropic API) | ✅ | Chave de API e créditos. Fica atrás da interface `LlmProvider` |
| OpenAI | ✅ | Chave de API e créditos |
| Modelo local (Ollama ou llama.cpp) | ✅ | Máquina com RAM ou GPU suficientes. Bom para classificação de e-mail sem enviar conteúdo para fora |
| MCP | ✅ | Servidor `jobfly-mcp` (stdio) para Claude Desktop e Claude Code, chamando a API com token de escopo restrito |

## Candidatura automática

Envio automático de formulário só em ambiente autorizado. O primeiro alvo previsto é um formulário de teste próprio (critério 21.7). Em ATS reais, a regra é preencher, pausar para revisão e enviar só com a política de autonomia permitindo, parando em qualquer CAPTCHA ou MFA. Os termos de cada plataforma precisam ser conferidos antes de ligar o envio automático nela.

## Congo Grey Parrot (Discord)

O plugin `integrations/congo/jobfly.py` roda dentro do bot de Discord do Congo Grey Parrot e usa a API do JobFly com um token pessoal.

| Item | Como funciona |
|---|---|
| Instalação | `scripts/conectar-congo.sh` copia o plugin para `congo-grey-parrot-discord/plugins/` e grava `~/.config/jobfly/congo.env` (permissão 600). O histórico do repositório do Congo não muda |
| Ativação | No Discord: `/funcoes ativar jobfly`. Plugins do Congo começam desligados |
| Comandos | `/jobfly resumo`, `/jobfly avisos`, `/jobfly pendencias`. Só a dona configurada recebe resposta, sempre privada (ephemeral) |
| Avisos | A cada 60 s (mínimo 30), os avisos com o canal "sistema" ligado viram mensagem direta, uma vez cada. Não marca como entregue: o painel aberto também mostra o pop-up |
| Privacidade | Respeita "esconder empresa e cargo"; mensagens sem menções (`allowed_mentions` vazio) e cortadas em 1800 caracteres |
| Segurança | API só local por HTTP ou HTTPS, sem redirecionamento, resposta limitada a 1 MiB; o token nunca aparece em mensagem, log ou erro; configuração com permissão frouxa é recusada |
| Custo | Nenhum: Discord e Congo são gratuitos |

Testes: `python3 -m unittest discover -s integrations/congo -p 'test_*.py'` (o teste do `setup()` com o discord.py roda com o Python do ambiente do Congo).

