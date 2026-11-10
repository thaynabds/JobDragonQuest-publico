# Runbook: operação e problemas comuns

| Situação | O que fazer |
|---|---|
| Subir tudo | `scripts/iniciar.sh` (PostgreSQL local, API e painel; só em 127.0.0.1) |
| Parar tudo | `scripts/iniciar.sh parar` |
| Banco | `scripts/postgres-local.sh iniciar \| parar \| status` (dados em `data/postgres`, porta 5433) |
| Logs | `data/logs/api.log`, `data/logs/web.log`, `data/logs/web-build.log`. Cada linha da API traz o `X-Request-Id` da resposta: procure por ele |
| Logs em JSON | `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` no `.env` |
| Saúde | `curl http://127.0.0.1:8080/actuator/health` deve responder `{"status":"UP"}` |
| "não respondeu em Ns" ao iniciar | Leia o log citado; confira Java 21 e Node 22.22.2+ e se a porta 8080 ou 5173 já está em uso (`ss -ltnp`) |
| PostgreSQL não sobe depois de mover a pasta | `scripts/postgres-local.sh parar` e `iniciar` de novo (o socket é recriado) |
| Coleta de uma fonte falhando | O painel mostra a mensagem e a próxima tentativa (recuo exponencial). Confira o identificador do board no ATS |
| MCP não conecta | `node scripts/conectar-mcp.mjs --cliente <cliente>` de novo com um token novo da tela Tokens |
| Conferir o sistema rodando | `scripts/testar-api.py`, `scripts/testar-avisos.py`, `scripts/testar-coleta.py` |
| Antes de commitar | `scripts/instalar-hooks.sh` uma vez; o CI repete lint, testes e análise de dependências |
