# JobFly MCP

Servidor MCP (stdio) para uma IA usar o JobFly que roda no seu computador. O cliente de IA inicia este programa e conversa com ele sem abrir porta de rede.

## Configuração

1. Suba o JobFly (`scripts/iniciar.sh`) e crie um token em **Configurações → Integrações e tokens de acesso** no painel.
2. Compile uma vez: `npm ci && npm run build` nesta pasta.
3. Registre no cliente de IA. O jeito mais fácil é `node scripts/conectar-mcp.mjs` na raiz do projeto, que lê o token sem mostrá-lo. À mão:

```json
{
  "mcpServers": {
    "jobfly": {
      "command": "node",
      "args": ["/caminho/para/JobFly/src/jobfly-mcp/dist/index.js"],
      "env": { "JOBFLY_TOKEN_FILE": "/home/voce/.config/jobfly/mcp-token", "JOBFLY_API_URL": "http://127.0.0.1:8080" }
    }
  }
}
```

| Variável | Padrão | Regra |
|---|---|---|
| `JOBFLY_TOKEN_FILE` | — | Recomendada. Arquivo com o token (permissão 600): a configuração do cliente guarda só o caminho |
| `JOBFLY_TOKEN` | — | Alternativa: o token `jfp_…` direto na variável |
| `JOBFLY_API_URL` | `http://127.0.0.1:8080` | Só a própria máquina por HTTP. Servidor remoto exige HTTPS e `JOBFLY_ALLOW_REMOTE=1` |

## Ferramentas

`jobfly_resumo`, `jobfly_pendencias`, `jobfly_avisos`, `jobfly_marcar_aviso_lido`, `jobfly_listar_perfis`, `jobfly_listar_vagas`, `jobfly_ver_vaga`, `jobfly_compatibilidade`, `jobfly_cadastrar_vaga`, `jobfly_listar_candidaturas`, `jobfly_ver_candidatura`, `jobfly_registrar_candidatura`, `jobfly_atualizar_etapa`, `jobfly_adicionar_nota`, `jobfly_listar_fontes`, `jobfly_coletar_fonte` e `jobfly_cadastrar_fontes`.

## Segurança

- Nenhuma ferramenta apaga dados, mexe em tokens ou na conta. Um token não consegue criar outros tokens.
- Argumentos validados (ids UUID, URLs http/https, enums fechados, tamanhos máximos) antes de chamar a API; caminhos montados por segmentos codificados.
- Mudança de etapa sempre como fonte `AGENT`: com confiança abaixo do mínimo, vira sugestão que você confirma no painel.
- Respostas marcadas como dados não confiáveis (vagas e e-mails vêm de fora) e limitadas a 60 mil caracteres. O token nunca aparece em resposta nem em erro.
- Revogar o token no painel corta o acesso na hora.

Testes: `npm run verify`. Teste contra a API real: `node scripts/verificar/mcp-real.mjs` na raiz.
