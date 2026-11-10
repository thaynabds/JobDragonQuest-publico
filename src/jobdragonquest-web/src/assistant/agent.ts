// Laço do chat: o modelo pede ferramentas, o painel executa com a sessão da própria pessoa e devolve o resultado.
// As ferramentas são as mesmas do servidor MCP (mesmas validações e limites). Defesas contra prompt injection
// (texto de vaga, e-mail ou documento tentando mandar no modelo):
// - argumentos validados pelo schema da ferramenta antes de qualquer chamada;
// - toda ferramenta que muda dados espera a pessoa permitir, mostrando o que vai ser feito;
// - nenhuma ferramenta apaga dados, mexe em tokens ou na conta (garantido pela lista do MCP);
// - número de passos limitado, para um modelo em laço não fazer centenas de chamadas.
import { REGRAS } from "@mcp/assistentes";
import type { Client } from "@mcp/client";
import { formatResult, TOOLS } from "@mcp/tools";
import { z } from "zod";

import { request, sendFile } from "../api/client";
import type { ChatMessage, Reply, ToolCall, ToolSpec } from "./providers";

export type AnyTool = (typeof TOOLS)[number];

export const MAX_STEPS = 8;

export const SYSTEM_PROMPT = [
  "Você é o assistente do JobDragonQuest, dentro do painel da própria pessoa. Use as ferramentas jobdragonquest_* para consultar e registrar dados da conta dela.",
  ...REGRAS,
  "Antes de uma ação que muda dados, diga em uma frase o que vai fazer: o painel pede a confirmação da pessoa.",
  "Responda no idioma da pessoa, em texto simples e curto.",
].join("\n");

export function toolSpecs(tools: readonly AnyTool[] = TOOLS): ToolSpec[] {
  return tools.map((tool) => {
    const parameters = z.toJSONSchema(tool.schema as z.ZodType, { unrepresentable: "any" }) as Record<string, unknown>;
    delete parameters.$schema;
    return { name: tool.name, description: tool.description, parameters };
  });
}

/** As ferramentas falam com a API pela sessão do painel (cookie e token em memória), nunca por um token guardado. */
export const panelClient: Client = {
  async request<T>(method: "GET" | "POST" | "PUT", path: string, body?: unknown, headers?: Record<string, string>): Promise<T> {
    if (!path.startsWith("/api/v1/")) throw new Error("Caminho de API inválido");
    if (body instanceof FormData) return (await sendFile<T>(path, body)).data;
    return (await request<T>(path, { method, body, headers })).data;
  },
};

export interface AgentOptions {
  history: ChatMessage[];
  send: (history: ChatMessage[], tools: ToolSpec[]) => Promise<Reply>;
  /** Pergunta à pessoa se a ação pode ser feita. */
  confirm: (tool: AnyTool, args: unknown) => Promise<boolean>;
  onUpdate: (history: ChatMessage[]) => void;
  client?: Client;
  tools?: readonly AnyTool[];
  maxSteps?: number;
}

async function runCall(call: ToolCall, options: AgentOptions, tools: readonly AnyTool[]): Promise<{ result: string; isError: boolean }> {
  const tool = tools.find((candidate) => candidate.name === call.name);
  if (!tool) return { result: `Ferramenta desconhecida: ${call.name.slice(0, 80)}`, isError: true };
  const parsed = (tool.schema as z.ZodType).safeParse(call.args ?? {});
  if (!parsed.success) {
    return { result: `Argumentos inválidos: ${parsed.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ").slice(0, 500)}`, isError: true };
  }
  if (!tool.readOnly && !(await options.confirm(tool, parsed.data))) {
    return { result: "A pessoa não permitiu esta ação. Não tente de novo sem ela pedir.", isError: true };
  }
  try {
    const data = await tool.run(options.client ?? panelClient, parsed.data as never);
    return { result: formatResult(data), isError: false };
  } catch (error) {
    const message = error instanceof Error ? error.message.slice(0, 300) : "falha";
    return { result: `Falha ao chamar o JobDragonQuest: ${message}`, isError: true };
  }
}

/** Roda até o modelo responder sem pedir ferramenta ou até o limite de passos. Devolve o histórico completo. */
export async function runAgent(options: AgentOptions): Promise<{ history: ChatMessage[]; limited: boolean }> {
  const tools = options.tools ?? TOOLS;
  const specs = toolSpecs(tools);
  let history = options.history;
  for (let step = 0; step < (options.maxSteps ?? MAX_STEPS); step++) {
    const reply = await options.send(history, specs);
    const calls = reply.calls.slice(0, 5);
    history = [...history, { role: "assistant", text: reply.text, calls }];
    options.onUpdate(history);
    if (calls.length === 0) return { history, limited: false };
    for (const call of calls) {
      const { result, isError } = await runCall(call, options, tools);
      history = [...history, { role: "tool", callId: call.id, name: call.name, result, isError }];
      options.onUpdate(history);
    }
  }
  return { history, limited: true };
}
