# 06. Segurança

Modelo de ameaça resumido. Quem pode atacar:
1. outro usuário da plataforma tentando ler dados alheios;
2. um site aberto no navegador da usuária (CSRF, CORS);
3. conteúdo externo hostil (e-mail, página de vaga, publicação) tentando virar instrução para a IA ou injetar HTML;
4. alguém com o banco ou os logs em mãos.

## Controles implementados e testados

| Ameaça | Controle | Teste |
|---|---|---|
| Acesso a dado de outro usuário (IDOR) | Toda consulta filtra por `user_id` do JWT. Recurso alheio responde 404 | `*IntegrationTest.anotherUser…`, `otherUsers…` |
| Senha fraca ou vazada do banco | Argon2id (Spring Security), mínimo de 12 e máximo de 128 caracteres | `AuthIntegrationTest.weakPassword…` |
| Força bruta | Limite por IP + e-mail (janela de 5 min) e bloqueio de 15 min após 5 falhas, registrado na auditoria | `SecurityUnitTest`, `accountLocks…` |
| Enumeração de contas no login | Mesma mensagem e mesmo custo (hash falso) para e-mail inexistente e senha errada | `loginErrorsDoNotReveal…` |
| Roubo de refresh token | Só o hash fica no banco. Rotação a cada uso. Reuso revoga a família. Trava pessimista contra corrida | `refreshRotates…` |
| CSRF | API sem cookie de sessão. O cookie de refresh é `SameSite=Strict` e restrito ao caminho `auth`. CORS com lista de origens. Conferência de `Origin` | `refreshFromForeignOrigin…` |
| JWT forjado ou trocado | HS256 fixo, segredo ≥ 32 bytes (a aplicação não sobe sem ele), emissor e audiência validados, 15 min | `protectedEndpointsRequire…`, `refusesToStart…` |
| XSS por link | URLs só `http`/`https`, sem `user:senha@`. `javascript:`, `data:` e `file:` são recusados | `UrlCanonicalizerTest`, `duplicateNameSkillAndBadData…` |
| SQL injection | JPA com parâmetros. Filtro `LIKE` com curingas escapados. Nenhuma concatenação de SQL | revisão de código |
| Abuso de tamanho | Corpo ≤ 1 MiB (filtro), listas e textos com limite em cada DTO, paginação ≤ 100 | `RequestSizeLimitFilterTest` |
| Vazamento por erro | Problem Details sem stack trace e sem SQL. Erros inesperados só no log do servidor | `GlobalExceptionHandler` |
| Headers | CSP `default-src 'none'`, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer` | `securityHeadersArePresent` |
| Métricas expostas | `/actuator/prometheus` só para ADMIN. `health` sem detalhes | `healthIsPublicButMetricsAreNot` |
| Edição concorrente | `@Version` com 409 `stale_version` | `staleVersionIsRejected` |
| Segredo no Git | `.env` ignorado, `.env.example` sem valores, script gera segredos locais com permissão 600 e não mostra nada na tela | `.gitignore` |

## Regras para os próximos incrementos

1. **Prompt injection.** Texto de e-mail, página ou documento entra no LLM delimitado como dado (`<conteudo_nao_confiavel>`), nunca como instrução. As ferramentas dos agentes são escolhidas pelo supervisor a partir da política, nunca a partir do conteúdo. Ação com efeito externo (enviar e-mail, enviar candidatura) passa pela política de autonomia e fica auditada.
2. **SSRF.** O cadastro por link vai buscar páginas. A busca só aceita http(s), resolve o DNS e recusa IP privado, loopback, link-local e metadados de nuvem (169.254.169.254), sem seguir redirecionamento para esses endereços. Tem limite de tamanho e de tempo.
3. **Tokens OAuth de e-mail:** cifrados com AES-GCM e chave fora do banco. Escopos mínimos (ver 04). Revogação na tela de integrações.
4. **Renderização:** a evidência e o corpo de e-mail são exibidos como texto no React (sem `dangerouslySetInnerHTML`).
5. **MCP:** token de escopo próprio, validação de argumentos, limite de chamadas e nenhuma ferramenta de exclusão.

## Limitações conhecidas

- O limite de tentativas fica em memória, por instância. Com mais de uma réplica, ele precisa ir para o Redis.
- `POST /auth/register` responde 409 para e-mail já cadastrado, o que revela que a conta existe. É um compromisso comum, mitigado pelo limite de tentativas. A alternativa (confirmação por e-mail) está no roadmap.
- `forward-headers-strategy` vale `none`: atrás de um proxy, configure-o, ou o limite de tentativas verá só o IP do proxy.
- A cifragem do banco em repouso depende do PostgreSQL ou do disco (não configurada aqui).
