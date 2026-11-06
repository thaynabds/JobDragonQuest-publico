# JobFly

> Idioma: **Português** | [English](README.en.md) | [Español](README.es.md)

Plataforma para gerenciar a carreira e automatizar candidaturas a vagas de emprego e estágio, no Brasil e no exterior. O JobFly reúne num só lugar tudo o que acontece em cada processo seletivo, venha a candidatura de onde vier: LinkedIn, Gupy, site da empresa, Instagram, grupo do Facebook, e-mail ou indicação.

**Autora:** [Thayná Batista da Silva](https://github.com/thaynabds) · **Situação:** em desenvolvimento, incremento 1 (núcleo da API).

## O que a solução faz

### Já funciona (incremento 1, testado)

- **Contas seguras.** Cadastro e login com senha protegida por Argon2id, sessão de 15 minutos renovada por um token rotativo que se revoga sozinho se for roubado, bloqueio após tentativas erradas e limite de requisições.
- **Vários perfis profissionais.** Por exemplo: "Desenvolvedora Java", "Estágio em Desenvolvimento", "Vagas internacionais". Cada perfil tem objetivo, cargos desejados, competências com nível e tempo de uso, idiomas no padrão CEFR, experiências, formação, projetos, cursos, certificações, pretensão salarial, modalidades, cidades, disponibilidade e restrições. Os perfis nunca se misturam.
- **Cadastro de vagas com deduplicação.** A mesma vaga chega por URLs diferentes (com rastreio, outro subdomínio, outra rota do LinkedIn) e o JobFly reconhece que é uma só. Também reconhece a mesma empresa e cargo, ignorando "Ltda", "S.A." e acentos.
- **Análise de compatibilidade explicável.**
  - Nota de 0 a 100, com o motivo de cada requisito: atendido, parcial, não atendido ou "precisa de leitura humana".
  - Impedimentos eliminatórios (modalidade, cidade, idioma exigido, senioridade) ficam separados: uma nota alta não esconde um impedimento.
  - O sistema escolhe sozinho o perfil mais adequado para cada vaga.
- **Central unificada de candidaturas.**
  - Registra candidaturas feitas em qualquer lugar e impede duplicatas por URL, por empresa e cargo, e por chave de idempotência.
  - Acompanha 19 etapas: da vaga encontrada até aprovada, reprovada ou encerrada, passando por triagem, entrevistas, testes técnicos e proposta.
- **Atualização por evidência.**
  - Cada mudança de etapa registra a origem (você, e-mail, portal, agente), o texto que a justifica e a confiança.
  - Uma confirmação automática nunca vira aprovação, e o silêncio nunca vira reprovação.
  - Informação incerta vira sugestão para você aceitar ou descartar.
  - Fontes automáticas não reabrem um processo encerrado. Só você pode.
- **Painel (API).** Kanban por etapa, visão geral com contadores, filtros por perfil, empresa, país, plataforma e período, histórico completo de cada candidatura e central de pendências (ação sua, teste técnico, entrevista próxima, prazo, sugestão a revisar, processo sem atualização).
- **Auditoria** de todas as operações relevantes, sem guardar conteúdo sensível.

### Próximos incrementos

O plano detalhado e a situação de cada critério de aceitação estão em [docs/roadmap.md](docs/roadmap.md). Resumo:

1. Painel web em React (português e inglês).
2. Busca automática de vagas pelas APIs públicas do Greenhouse, Lever, SmartRecruiters e Ashby, e lista de Empresas Monitoradas.
3. Gmail e Outlook por OAuth com permissões mínimas: classificação de mensagens, ligação automática com a candidatura certa, marcadores e respostas autorizadas. Nunca apaga e-mails.
4. Agentes de IA (Claude, OpenAI ou modelo local): análise de perguntas eliminatórias, revisão da candidatura antes do envio e documentos personalizados que usam só dados reais do perfil.
5. Servidor MCP para usar as ferramentas do JobFly no Claude Desktop e no Claude Code.
6. Extensão de navegador "Enviar ao JobFly".
7. Currículos versionados, anexos e importação de histórico (CSV).

## Princípios

- **Veracidade.** A IA nunca inventa experiência, certificado, idioma ou competência.
- **Regras das plataformas.** Só APIs oficiais e caminhos permitidos. CAPTCHA, MFA e bloqueios nunca são contornados: o JobFly avisa o que depende de você.
- **Você no controle.** Aceitar proposta, assinar contrato, assumir compromisso financeiro ou compartilhar dado sensível sempre pede a sua aprovação.
- **Privacidade (LGPD/GDPR).** Dados separados por usuário, segredos fora do código, exclusão em cascata.

## Arquitetura

Monólito modular em Java 21 e Spring Boot 4.1, com PostgreSQL e Flyway. Os módulos são separados por responsabilidade (`identity`, `profiles`, `jobs`, `matching`, `applications`, `dashboard`, `audit`) e preparados para virar serviços próprios quando houver motivo. As decisões e suas justificativas estão em [docs/arquitetura.md](docs/arquitetura.md).

```
JobFly/
├── src/jobfly-api/           API REST (Spring Boot)
├── docs/                    requisitos, arquitetura, dados, integrações, API, segurança, roadmap
├── docs/referencias-jobcopilot/   estudo de referência e matriz de funcionalidades
└── scripts/                 configuração e execução local
```

## Como executar

Requisitos: Java 21 e PostgreSQL 14 ou mais novo instalado (só os programas, ele não precisa estar configurado). O Maven vem embutido (`mvnw`).

```bash
# 1. Cria um PostgreSQL só seu em data/postgres (127.0.0.1:5433, sem sudo)
#    e grava .env com senhas e segredo JWT aleatórios
scripts/configurar-local.sh

# 2. Sobe a API em http://127.0.0.1:8080 (só esta máquina alcança)
scripts/iniciar-api.sh

# 3. Em outro terminal: percorre o fluxo completo contra a API rodando
scripts/testar-api.py

# 4. Testes automatizados, com cobertura em src/jobfly-api/target/site/jacoco
cd src/jobfly-api && ./mvnw verify
```

Para parar: `Ctrl+C` na API e `scripts/postgres-local.sh parar` no banco. Depois de reiniciar o computador, rode `scripts/postgres-local.sh iniciar` antes da API. Se preferir o PostgreSQL do sistema (porta 5432), use `scripts/configurar-local.sh --sistema` (pede sudo).

As variáveis de ambiente estão em [.env.example](.env.example). A referência da API está em [docs/api.md](docs/api.md).

## Qualidade e segurança

- 81 testes automatizados (unitários e de integração com a aplicação completa).
- O build falha se a cobertura de linhas dos módulos críticos ficar abaixo de 80% (hoje está acima de 92%).
- Controles e testes de segurança estão em [docs/seguranca.md](docs/seguranca.md): isolamento por usuário, CSRF, XSS, injeção, força bruta, roubo de token e tamanho de requisição.

## Licença

Todos os direitos reservados. Repositório privado.
