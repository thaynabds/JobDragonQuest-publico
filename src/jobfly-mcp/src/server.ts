import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import { ApiError, type Client } from "./client.js";
import { formatResult, TOOLS } from "./tools.js";

export const SERVER_NAME = "jobfly";

/** Registra as ferramentas. Erros viram texto para a IA (com isError), sem pilha nem token. */
export function buildServer(client: Client, version: string): McpServer {
  const server = new McpServer({ name: SERVER_NAME, version });
  for (const tool of TOOLS) {
    server.registerTool(
      tool.name,
      {
        title: tool.title,
        description: tool.description,
        inputSchema: tool.schema.shape,
        annotations: { readOnlyHint: tool.readOnly, destructiveHint: false, openWorldHint: false },
      },
      async (args: unknown) => {
        const parsed = tool.schema.safeParse(args ?? {});
        if (!parsed.success) {
          return { isError: true, content: [{ type: "text" as const, text: `Argumentos inválidos: ${parsed.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ").slice(0, 500)}` }] };
        }
        try {
          // O schema de cada ferramenta já validou os argumentos acima.
          const data = await tool.run(client, parsed.data as never);
          return { content: [{ type: "text" as const, text: formatResult(data) }] };
        } catch (error) {
          const message = error instanceof ApiError ? `${error.message} (${error.code})` : "Falha inesperada ao chamar o JobFly";
          return { isError: true, content: [{ type: "text" as const, text: message }] };
        }
      },
    );
  }
  return server;
}
