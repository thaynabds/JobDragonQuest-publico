# Deploy opcional

O JobFly foi feito para rodar no seu computador (`scripts/instalar.sh`), sem deploy e sem custo. Publicar na internet é opcional e só vale a pena se você precisar abrir o painel fora de casa. Este guia só usa opções **gratuitas**: se algum serviço pedir pagamento ou cartão para continuar, pare e escolha outra opção.

## Opção 1 (recomendada): continuar local e abrir pelo celular

Nada fica exposto na internet.

1. Instale o [Tailscale](https://tailscale.com/pricing) (plano pessoal gratuito) no computador e no celular, entrando com a mesma conta.
2. No computador, exponha o painel só para a sua rede privada do Tailscale: `tailscale serve --bg 5173`. Anote o endereço que ele mostrar, por exemplo `https://meu-pc.minha-rede.ts.net`.
3. Suba o JobFly aceitando esse endereço (troque pelo seu):

   ```bash
   scripts/iniciar.sh parar
   JOBFLY_ALLOWED_HOSTS=meu-pc.minha-rede.ts.net \
   JOBFLY_ALLOWED_ORIGINS=http://127.0.0.1:5173,https://meu-pc.minha-rede.ts.net \
   scripts/iniciar.sh
   ```

   `JOBFLY_ALLOWED_HOSTS` deixa o painel responder por esse nome; `JOBFLY_ALLOWED_ORIGINS` deixa o login funcionar a partir dele.
4. No celular, abra o endereço do passo 2.

Os avisos chegam enquanto o painel estiver aberto. Com o painel fechado, use o plugin do Congo Grey Parrot (`scripts/conectar-congo.sh`) para receber por mensagem direta no Discord, também de graça.

## Opção 2: publicar na internet com planos gratuitos

Os planos gratuitos mudam com o tempo: confira as condições de cada serviço antes de começar. Limitações comuns: a API "dorme" depois de alguns minutos sem uso (a primeira abertura demora) e, dormindo, a coleta automática e os lembretes não rodam.

| Parte | Exemplo de serviço com plano gratuito | Observação |
|---|---|---|
| Banco | Neon (PostgreSQL) | Pegue a URL de conexão JDBC com `sslmode=require` |
| API | Render (Web Service com Docker) | Usa `src/jobfly-api/Dockerfile` |
| Painel | Cloudflare Pages ou Vercel (site estático) | Build `npm ci && npm run build` em `src/jobfly-web`, pasta `dist` |

### Lista de segurança (obrigatória antes de publicar)

- [ ] `JOBFLY_ALLOWED_EMAILS` com o seu e-mail: sem isso, qualquer pessoa cria conta no seu servidor.
- [ ] `JOBFLY_JWT_SECRET` novo e aleatório (`openssl rand -base64 32`), guardado só nas variáveis da plataforma.
- [ ] Senha do banco forte e só nas variáveis da plataforma; nunca no repositório.
- [ ] HTTPS em tudo (as plataformas acima já entregam). `JOBFLY_SECURE_COOKIES=true` (padrão da imagem).
- [ ] O painel encaminha `/api/*` para a API (rewrite/proxy da plataforma), para o login e o cookie de sessão funcionarem no mesmo endereço. Se o painel e a API ficarem em endereços diferentes, ponha o endereço do painel em `JOBFLY_ALLOWED_ORIGINS`.
- [ ] O repositório continua privado (ou sem nenhum segredo, se ficar público).
- [ ] Para o MCP e o Congo usarem o servidor público: API em HTTPS e `JOBFLY_ALLOW_REMOTE=1` no MCP.

### Variáveis da API

| Variável | Valor |
|---|---|
| `JOBFLY_DB_URL` | `jdbc:postgresql://<host>/<banco>?sslmode=require` |
| `JOBFLY_DB_USER` e `JOBFLY_DB_PASSWORD` | Usuário e senha do banco |
| `JOBFLY_JWT_SECRET` | Segredo novo em Base64 (32 bytes ou mais) |
| `JOBFLY_ALLOWED_EMAILS` | Seu e-mail (vários separados por vírgula) |
| `JOBFLY_ALLOWED_ORIGINS` | Endereço do painel, se for diferente do da API |

A imagem já define `JOBFLY_ADDRESS=0.0.0.0` e `JOBFLY_FORWARD_HEADERS=native` (o HTTPS fica no proxy da plataforma).

### Encaminhar /api no painel

Exemplo para o Vercel (`vercel.json` dentro de `src/jobfly-web`), trocando pelo endereço da sua API:

```json
{
  "rewrites": [
    { "source": "/api/:path*", "destination": "https://SUA-API.onrender.com/api/:path*" },
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

No Cloudflare Pages, o equivalente é um arquivo `_redirects` com `/api/* https://SUA-API.onrender.com/api/:splat 200` seguido de `/* /index.html 200`.

### O que não foi testado aqui

A imagem Docker não foi construída nesta máquina (sem Docker instalado), o Tailscale não foi testado (não está instalado aqui) e nenhum deploy foi feito: os serviços exigem contas que só você pode criar. Teste com cuidado e, se algo pedir pagamento, não continue.
