#!/usr/bin/env node
// Servidor MCP do JobFly por stdio: o cliente de IA (Claude Desktop, Claude Code, Cursor…) inicia este programa e
// conversa com ele sem abrir porta de rede. Configure JOBFLY_TOKEN (token pessoal) e, se preciso, JOBFLY_API_URL.
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";

import { createClient } from "./client.js";
import { ConfigError, loadConfig } from "./config.js";
import { buildServer } from "./server.js";

const VERSION = "0.3.0";

try {
  const config = loadConfig(process.env);
  const server = buildServer(createClient(config), VERSION);
  await server.connect(new StdioServerTransport());
  // stdout é do protocolo: mensagens para pessoas vão para stderr.
  console.error(`JobFly MCP ${VERSION} conectado a ${config.apiUrl}`);
} catch (error) {
  console.error(error instanceof ConfigError ? `Configuração inválida: ${error.message}` : "Falha ao iniciar o servidor MCP do JobFly");
  process.exit(1);
}
