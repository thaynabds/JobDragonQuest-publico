// Conversa com o modelo escolhido, direto do navegador, sem SDK. Os destinos são fixos (Anthropic, OpenAI) ou o
// Ollama no próprio computador; a chave vai só no cabeçalho do provedor e nunca aparece em mensagem de erro.
import type { AiSettings } from "./settings";
import { isLoopbackUrl } from "./settings";

export interface ToolCall {
  id: string;
  name: string;
  args: unknown;
}

export type ChatMessage =
  | { role: "user"; text: string }
  | { role: "assistant"; text: string; calls: ToolCall[] }
  | { role: "tool"; callId: string; name: string; result: string; isError: boolean };

export interface ToolSpec {
  name: string;
  description: string;
  parameters: Record<string, unknown>;
}

export interface Reply {
  text: string;
  calls: ToolCall[];
}

export class AiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "AiError";
  }
}

const TIMEOUT_MS = 180_000;
const MAX_RESPONSE_CHARS = 2_000_000;
const MAX_TOKENS = 2048;

async function post(url: string, headers: Record<string, string>, body: unknown, fetchImpl: typeof fetch): Promise<unknown> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetchImpl(url, {
      method: "POST",
      redirect: "error",
      credentials: "omit",
      referrerPolicy: "no-referrer",
      signal: controller.signal,
      headers: { "Content-Type": "application/json", ...headers },
      body: JSON.stringify(body),
    });
  } catch {
    throw new AiError(0, "unreachable");
  } finally {
    clearTimeout(timer);
  }
  const text = await response.text();
  if (text.length > MAX_RESPONSE_CHARS) throw new AiError(response.status, "too_large");
  if (!response.ok) {
    // Só o texto do provedor, cortado. Ele nunca repete a chave, e a chave nunca entra aqui.
    let detail = `HTTP ${response.status}`;
    try {
      const parsed = JSON.parse(text) as { error?: { message?: unknown } | string };
      const message = typeof parsed.error === "string" ? parsed.error : parsed.error?.message;
      if (typeof message === "string") detail = message.slice(0, 300);
    } catch {
      // Corpo sem JSON.
    }
    throw new AiError(response.status, detail);
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    throw new AiError(response.status, "invalid_json");
  }
}

function parseArgs(raw: unknown): unknown {
  if (typeof raw !== "string") return raw ?? {};
  try {
    return JSON.parse(raw) as unknown;
  } catch {
    return { __invalido: raw.slice(0, 200) };
  }
}

// ---- Anthropic (Messages API) ----

function anthropicMessages(history: ChatMessage[]) {
  const out: { role: "user" | "assistant"; content: unknown }[] = [];
  for (const message of history) {
    if (message.role === "user") {
      out.push({ role: "user", content: message.text });
    } else if (message.role === "assistant") {
      const content: unknown[] = [];
      if (message.text) content.push({ type: "text", text: message.text });
      message.calls.forEach((call) => content.push({ type: "tool_use", id: call.id, name: call.name, input: call.args ?? {} }));
      out.push({ role: "assistant", content });
    } else {
      const block = { type: "tool_result", tool_use_id: message.callId, content: message.result, is_error: message.isError };
      const last = out[out.length - 1];
      // Vários resultados seguidos vão juntos numa única mensagem da pessoa, como a API exige.
      if (last && last.role === "user" && Array.isArray(last.content)) {
        (last.content as unknown[]).push(block);
      } else {
        out.push({ role: "user", content: [block] });
      }
    }
  }
  return out;
}

async function anthropic(settings: AiSettings, system: string, history: ChatMessage[], tools: ToolSpec[], fetchImpl: typeof fetch): Promise<Reply> {
  const data = (await post(
    "https://api.anthropic.com/v1/messages",
    {
      "x-api-key": settings.apiKey.trim(),
      "anthropic-version": "2023-06-01",
      "anthropic-dangerous-direct-browser-access": "true",
    },
    {
      model: settings.model,
      max_tokens: MAX_TOKENS,
      system,
      messages: anthropicMessages(history),
      tools: tools.map((tool) => ({ name: tool.name, description: tool.description, input_schema: tool.parameters })),
    },
    fetchImpl,
  )) as { content?: { type: string; text?: string; id?: string; name?: string; input?: unknown }[] };
  const blocks = Array.isArray(data.content) ? data.content : [];
  return {
    text: blocks.filter((b) => b.type === "text" && typeof b.text === "string").map((b) => b.text).join("\n"),
    calls: blocks
      .filter((b) => b.type === "tool_use" && typeof b.name === "string")
      .map((b) => ({ id: String(b.id ?? crypto.randomUUID()), name: String(b.name), args: b.input ?? {} })),
  };
}

// ---- OpenAI (Chat Completions) e Ollama (/api/chat), que usam o mesmo formato de ferramentas ----

function openAiMessages(system: string, history: ChatMessage[], ollama: boolean) {
  const out: Record<string, unknown>[] = [{ role: "system", content: system }];
  for (const message of history) {
    if (message.role === "user") {
      out.push({ role: "user", content: message.text });
    } else if (message.role === "assistant") {
      const entry: Record<string, unknown> = { role: "assistant", content: message.text };
      if (message.calls.length > 0) {
        entry.tool_calls = message.calls.map((call) =>
          ollama
            ? { function: { name: call.name, arguments: call.args ?? {} } }
            : { id: call.id, type: "function", function: { name: call.name, arguments: JSON.stringify(call.args ?? {}) } },
        );
      }
      out.push(entry);
    } else {
      out.push(ollama ? { role: "tool", tool_name: message.name, content: message.result } : { role: "tool", tool_call_id: message.callId, content: message.result });
    }
  }
  return out;
}

type OpenAiMessage = { content?: unknown; tool_calls?: { id?: string; function?: { name?: string; arguments?: unknown } }[] };

function fromOpenAiMessage(message: OpenAiMessage | undefined): Reply {
  return {
    text: typeof message?.content === "string" ? message.content : "",
    calls: (Array.isArray(message?.tool_calls) ? message.tool_calls : [])
      .filter((call) => typeof call.function?.name === "string")
      .map((call) => ({ id: String(call.id ?? crypto.randomUUID()), name: String(call.function?.name), args: parseArgs(call.function?.arguments) })),
  };
}

const openAiTools = (tools: ToolSpec[]) => tools.map((tool) => ({ type: "function", function: tool }));

async function openai(settings: AiSettings, system: string, history: ChatMessage[], tools: ToolSpec[], fetchImpl: typeof fetch): Promise<Reply> {
  const data = (await post(
    "https://api.openai.com/v1/chat/completions",
    { Authorization: `Bearer ${settings.apiKey.trim()}` },
    { model: settings.model, messages: openAiMessages(system, history, false), tools: openAiTools(tools) },
    fetchImpl,
  )) as { choices?: { message?: OpenAiMessage }[] };
  return fromOpenAiMessage(data.choices?.[0]?.message);
}

async function ollama(settings: AiSettings, system: string, history: ChatMessage[], tools: ToolSpec[], fetchImpl: typeof fetch): Promise<Reply> {
  if (!isLoopbackUrl(settings.ollamaUrl)) throw new AiError(0, "bad_url");
  const url = new URL("/api/chat", settings.ollamaUrl).toString();
  const data = (await post(url, {}, { model: settings.model, stream: false, messages: openAiMessages(system, history, true), tools: openAiTools(tools) }, fetchImpl)) as {
    message?: OpenAiMessage;
  };
  return fromOpenAiMessage(data.message);
}

export function chat(settings: AiSettings, system: string, history: ChatMessage[], tools: ToolSpec[], fetchImpl: typeof fetch = fetch): Promise<Reply> {
  switch (settings.provider) {
    case "anthropic":
      return anthropic(settings, system, history, tools, fetchImpl);
    case "openai":
      return openai(settings, system, history, tools, fetchImpl);
    default:
      return ollama(settings, system, history, tools, fetchImpl);
  }
}
