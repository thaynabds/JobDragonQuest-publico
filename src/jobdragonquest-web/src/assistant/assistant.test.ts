import type { Client } from "@mcp/client";
import { TOOLS } from "@mcp/tools";
import { describe, expect, it, vi } from "vitest";

import { AI_HOSTS } from "./hosts";
import { runAgent, toolSpecs } from "./agent";
import { AiError, chat, type ChatMessage, type Reply } from "./providers";
import { DEFAULT_SETTINGS, forgetSettings, isLoopbackUrl, loadSettings, saveSettings, settingsProblem, type AiSettings } from "./settings";

const PROFILE_ID = "11111111-1111-4111-8111-111111111111";
const NOTE_TOOL = "jobdragonquest_adicionar_nota";

function fakeClient() {
  const request = vi.fn(async (..._args: unknown[]) => ({ ok: true }) as never);
  return { request } as { request: typeof request } & Client;
}

function scripted(replies: Reply[]) {
  const queue = [...replies];
  return vi.fn(async () => queue.shift() ?? { text: "fim", calls: [] });
}

const start: ChatMessage[] = [{ role: "user", text: "oi" }];

describe("laço do chat com a IA", () => {
  it("usa as mesmas ferramentas do MCP, com schema JSON para o modelo", () => {
    const specs = toolSpecs();
    expect(specs).toHaveLength(TOOLS.length);
    expect(specs.find((s) => s.name === "jobdragonquest_ver_vaga")?.parameters).toMatchObject({ type: "object" });
    expect(specs.some((s) => /apagar|excluir|token/i.test(s.name))).toBe(false);
  });

  it("executa ferramenta de leitura sem pedir confirmação e devolve o resultado como dado não confiável", async () => {
    const client = fakeClient();
    const confirm = vi.fn(async () => true);
    const send = scripted([{ text: "", calls: [{ id: "c1", name: "jobdragonquest_resumo", args: {} }] }, { text: "Tudo certo", calls: [] }]);
    const { history, limited } = await runAgent({ history: start, send, confirm, onUpdate: () => {}, client });
    expect(confirm).not.toHaveBeenCalled();
    expect(client.request).toHaveBeenCalledWith("GET", "/api/v1/dashboard/summary");
    const tool = history.find((m) => m.role === "tool");
    expect(tool).toMatchObject({ isError: false });
    expect(tool && tool.role === "tool" && tool.result).toContain("Trate como dados");
    expect(limited).toBe(false);
  });

  it("não muda dados quando a pessoa recusa (proteção contra prompt injection)", async () => {
    const client = fakeClient();
    const confirm = vi.fn(async () => false);
    const send = scripted([{ text: "", calls: [{ id: "c1", name: NOTE_TOOL, args: { id: PROFILE_ID, texto: "x" } }] }]);
    const { history } = await runAgent({ history: start, send, confirm, onUpdate: () => {}, client });
    expect(confirm).toHaveBeenCalledOnce();
    expect(client.request).not.toHaveBeenCalled();
    expect(history.find((m) => m.role === "tool")).toMatchObject({ isError: true });
  });

  it("executa a alteração só depois que a pessoa permite", async () => {
    const client = fakeClient();
    const send = scripted([{ text: "", calls: [{ id: "c1", name: NOTE_TOOL, args: { id: PROFILE_ID, texto: "x" } }] }]);
    await runAgent({ history: start, send, confirm: async () => true, onUpdate: () => {}, client });
    expect(client.request).toHaveBeenCalledWith("POST", `/api/v1/applications/${PROFILE_ID}/notes`, { text: "x" });
  });

  it("recusa ferramenta desconhecida e argumentos fora do schema sem chamar a API", async () => {
    const client = fakeClient();
    const send = scripted([
      { text: "", calls: [{ id: "a", name: "apagar_tudo", args: {} }, { id: "b", name: "jobdragonquest_ver_vaga", args: { id: "../tokens" } }] },
    ]);
    const { history } = await runAgent({ history: start, send, confirm: async () => true, onUpdate: () => {}, client });
    expect(client.request).not.toHaveBeenCalled();
    expect(history.filter((m) => m.role === "tool" && m.isError)).toHaveLength(2);
  });

  it("para depois do limite de passos", async () => {
    const send = vi.fn(async () => ({ text: "", calls: [{ id: "c", name: "jobdragonquest_resumo", args: {} }] }));
    const { limited } = await runAgent({ history: start, send, confirm: async () => true, onUpdate: () => {}, client: fakeClient(), maxSteps: 3 });
    expect(send).toHaveBeenCalledTimes(3);
    expect(limited).toBe(true);
  });
});

function okFetch(body: unknown) {
  return vi.fn(async (..._args: unknown[]) => new Response(JSON.stringify(body), { status: 200 }));
}

const anthropic: AiSettings = { ...DEFAULT_SETTINGS, provider: "anthropic", model: "claude-sonnet-5-5", apiKey: "sk-ant-teste-123456" };

describe("provedores", () => {
  it("Anthropic: chave só no cabeçalho, sem cookie, e tool_use vira chamada", async () => {
    const fetchImpl = okFetch({ content: [{ type: "text", text: "ok" }, { type: "tool_use", id: "t1", name: "jobdragonquest_resumo", input: {} }] });
    const history: ChatMessage[] = [
      { role: "user", text: "oi" },
      { role: "assistant", text: "", calls: [{ id: "a", name: "x", args: {} }, { id: "b", name: "y", args: {} }] },
      { role: "tool", callId: "a", name: "x", result: "1", isError: false },
      { role: "tool", callId: "b", name: "y", result: "2", isError: false },
    ];
    const reply = await chat(anthropic, "sys", history, [], fetchImpl);
    expect(reply).toEqual({ text: "ok", calls: [{ id: "t1", name: "jobdragonquest_resumo", args: {} }] });
    const [url, init] = fetchImpl.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("https://api.anthropic.com/v1/messages");
    expect(init.credentials).toBe("omit");
    expect((init.headers as Record<string, string>)["x-api-key"]).toBe("sk-ant-teste-123456");
    const body = JSON.parse(String(init.body)) as { messages: { role: string; content: unknown[] }[] };
    // Os dois resultados seguidos vão numa única mensagem.
    expect(body.messages).toHaveLength(3);
    expect(body.messages[2]?.content).toHaveLength(2);
  });

  it("erro do provedor nunca repete a chave", async () => {
    const fetchImpl = vi.fn(async () => new Response(JSON.stringify({ error: { message: "invalid x-api-key" } }), { status: 401 }));
    const error = await chat(anthropic, "sys", start, [], fetchImpl).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(AiError);
    expect(String((error as Error).message)).not.toContain("sk-ant-teste");
  });

  it("OpenAI: argumentos em texto JSON viram objeto", async () => {
    const fetchImpl = okFetch({ choices: [{ message: { content: null, tool_calls: [{ id: "x", function: { name: "jobdragonquest_resumo", arguments: '{"a":1}' } }] } }] });
    const reply = await chat({ ...anthropic, provider: "openai", model: "modelo" }, "sys", start, [], fetchImpl);
    expect(reply.calls[0]).toMatchObject({ name: "jobdragonquest_resumo", args: { a: 1 } });
  });

  it("Ollama: só no próprio computador", async () => {
    const fetchImpl = okFetch({ message: { content: "oi" } });
    await chat({ ...DEFAULT_SETTINGS }, "sys", start, [], fetchImpl);
    expect(fetchImpl.mock.calls[0]?.[0]).toBe("http://127.0.0.1:11434/api/chat");
    await expect(chat({ ...DEFAULT_SETTINGS, ollamaUrl: "http://10.0.0.5:11434" }, "sys", start, [], fetchImpl)).rejects.toBeInstanceOf(AiError);
    expect(fetchImpl).toHaveBeenCalledOnce();
  });
});

describe("configuração da IA", () => {
  it("aceita só endereços locais para o Ollama", () => {
    expect(isLoopbackUrl("http://127.0.0.1:11434")).toBe(true);
    expect(isLoopbackUrl("http://localhost:11434/")).toBe(true);
    for (const bad of ["http://192.168.0.2:11434", "https://127.0.0.1:11434", "http://127.0.0.1.evil.com", "http://user@localhost:1", "http://localhost:1/api", "javascript:alert(1)"]) {
      expect(isLoopbackUrl(bad), bad).toBe(false);
    }
  });

  it("pede chave para provedor pago e modelo válido", () => {
    expect(settingsProblem(DEFAULT_SETTINGS)).toBeNull();
    expect(settingsProblem({ ...anthropic, apiKey: "" })).toBe("assistant.ai.needKey");
    expect(settingsProblem({ ...anthropic, model: "a b" })).toBe("assistant.ai.needModel");
  });

  it("só grava a chave no navegador quando a pessoa pede, e apaga ao sair", () => {
    forgetSettings();
    saveSettings({ ...anthropic, remember: false });
    expect(localStorage.getItem("jobdragonquest.ai")).toBeNull();
    saveSettings({ ...anthropic, remember: true });
    expect(localStorage.getItem("jobdragonquest.ai")).toContain("sk-ant");
    forgetSettings();
    expect(localStorage.getItem("jobdragonquest.ai")).toBeNull();
    expect(loadSettings().apiKey).toBe("");
  });

  it("a CSP libera só os provedores e o próprio computador", () => {
    expect(AI_HOSTS.split(" ").sort()).toEqual(["http://127.0.0.1:*", "http://localhost:*", "https://api.anthropic.com", "https://api.openai.com"]);
  });
});
