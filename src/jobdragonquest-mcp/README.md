# JobDragonQuest MCP

Servidor MCP (stdio) para uma IA usar o JobDragonQuest que roda no seu computador. O cliente de IA inicia este programa e conversa com ele sem abrir porta de rede.

## Configuração

1. Suba o JobDragonQuest (`scripts/iniciar.sh`) e crie um token em **Configurações → Integrações e tokens de acesso** no painel.
2. Compile uma vez: `npm ci && npm run build` nesta pasta.
3. Registre no cliente de IA. O jeito mais fácil é `node scripts/conectar-mcp.mjs` na raiz do projeto, que lê o token sem mostrá-lo. À mão:

```json
{
  "mcpServers": {
    "jobdragonquest": {
      "command": "node",
      "args": ["/caminho/para/JobDragonQuest/src/jobdragonquest-mcp/dist/index.js"],
      "env": { "JOBDRAGONQUEST_TOKEN_FILE": "/home/voce/.config/jobdragonquest/mcp-token", "JOBDRAGONQUEST_API_URL": "http://127.0.0.1:8080" }
    }
  }
}
```

| Variável | Padrão | Regra |
|---|---|---|
| `JOBDRAGONQUEST_TOKEN_FILE` | — | Recomendada. Arquivo com o token (permissão 600): a configuração do cliente guarda só o caminho |
| `JOBDRAGONQUEST_TOKEN` | — | Alternativa: o token `jdq_…` direto na variável |
| `JOBDRAGONQUEST_API_URL` | `http://127.0.0.1:8080` | Só a própria máquina por HTTP. Servidor remoto exige HTTPS e `JOBDRAGONQUEST_ALLOW_REMOTE=1` |

## Ferramentas

`jobdragonquest_resumo`, `jobdragonquest_pendencias`, `jobdragonquest_avisos`, `jobdragonquest_marcar_aviso_lido`, `jobdragonquest_listar_perfis`, `jobdragonquest_listar_vagas`, `jobdragonquest_ver_vaga`, `jobdragonquest_compatibilidade`, `jobdragonquest_cadastrar_vaga`, `jobdragonquest_listar_candidaturas`, `jobdragonquest_ver_candidatura`, `jobdragonquest_registrar_candidatura`, `jobdragonquest_atualizar_etapa`, `jobdragonquest_adicionar_nota`, `jobdragonquest_listar_fontes`, `jobdragonquest_coletar_fonte` e `jobdragonquest_cadastrar_fontes`.

## Segurança

- Nenhuma ferramenta apaga dados, mexe em tokens ou na conta. Um token não consegue criar outros tokens.
- Argumentos validados (ids UUID, URLs http/https, enums fechados, tamanhos máximos) antes de chamar a API; caminhos montados por segmentos codificados.
- Mudança de etapa sempre como fonte `AGENT`: com confiança abaixo do mínimo, vira sugestão que você confirma no painel.
- Respostas marcadas como dados não confiáveis (vagas e e-mails vêm de fora) e limitadas a 60 mil caracteres. O token nunca aparece em resposta nem em erro.
- Revogar o token no painel corta o acesso na hora.

Testes: `npm run verify`. Teste contra a API real: `node scripts/verificar/mcp-real.mjs` na raiz.
