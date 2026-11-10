import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { AssistantChat } from "./AssistantChat";
import { forgetSettings } from "./settings";

const client = vi.hoisted(() => ({ request: vi.fn(), sendFile: vi.fn() }));
vi.mock("../api/client", () => client);

const APPLICATION = "11111111-1111-4111-8111-111111111111";

function ollamaReplies(...messages: unknown[]) {
  const queue = [...messages];
  return vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response(JSON.stringify({ message: queue.shift() ?? { content: "fim" } })));
}

async function configureOllama(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "Usar esta IA" }));
}

beforeEach(() => {
  forgetSettings();
  client.request.mockReset();
  client.request.mockResolvedValue({ data: { ok: true } });
});

const renderChat = () =>
  render(
    <I18nProvider initial="pt-BR">
      <AssistantChat />
    </I18nProvider>,
  );

describe("chat com a IA no painel (#221)", () => {
  it("só libera a conversa depois de escolher a IA", async () => {
    const user = userEvent.setup({ delay: null });
    renderChat();
    expect((screen.getByRole("textbox", { name: "Mensagem" }) as HTMLTextAreaElement).disabled).toBe(true);
    await configureOllama(user);
    expect((screen.getByRole("textbox", { name: "Mensagem" }) as HTMLTextAreaElement).disabled).toBe(false);
  });

  it("consulta a conta e mostra a resposta da IA", async () => {
    const fetchMock = ollamaReplies({ content: "", tool_calls: [{ function: { name: "jobdragonquest_pendencias", arguments: {} } }] }, { content: "Você tem 2 pendências." });
    const user = userEvent.setup({ delay: null });
    renderChat();
    await configureOllama(user);
    await user.type(screen.getByRole("textbox", { name: "Mensagem" }), "o que tenho para fazer?");
    await user.click(screen.getByRole("button", { name: "Enviar" }));
    expect(await screen.findByText("Você tem 2 pendências.")).toBeTruthy();
    expect(client.request).toHaveBeenCalledWith("/api/v1/dashboard/pending", expect.objectContaining({ method: "GET" }));
    expect(screen.getByText("Usou: pendencias")).toBeTruthy();
    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://127.0.0.1:11434/api/chat");
  });

  it("pede permissão antes de alterar e não altera quando a pessoa recusa", async () => {
    ollamaReplies(
      { content: "Vou anotar.", tool_calls: [{ function: { name: "jobdragonquest_adicionar_nota", arguments: { id: APPLICATION, texto: "texto vindo de um e-mail" } } }] },
      { content: "Tudo bem, não anotei." },
    );
    const user = userEvent.setup({ delay: null });
    renderChat();
    await configureOllama(user);
    await user.type(screen.getByRole("textbox", { name: "Mensagem" }), "anote");
    await user.click(screen.getByRole("button", { name: "Enviar" }));
    const dialog = await screen.findByRole("alertdialog");
    expect(dialog.textContent).toContain("texto vindo de um e-mail");
    await user.click(screen.getByRole("button", { name: "Recusar" }));
    expect(await screen.findByText("Tudo bem, não anotei.")).toBeTruthy();
    expect(client.request).not.toHaveBeenCalled();
  });

  it("faz a alteração quando a pessoa permite", async () => {
    ollamaReplies(
      { content: "", tool_calls: [{ function: { name: "jobdragonquest_adicionar_nota", arguments: { id: APPLICATION, texto: "ok" } } }] },
      { content: "Anotado." },
    );
    const user = userEvent.setup({ delay: null });
    renderChat();
    await configureOllama(user);
    await user.type(screen.getByRole("textbox", { name: "Mensagem" }), "anote");
    await user.click(screen.getByRole("button", { name: "Enviar" }));
    await user.click(await screen.findByRole("button", { name: "Permitir" }));
    expect(await screen.findByText("Anotado.")).toBeTruthy();
    await waitFor(() => expect(client.request).toHaveBeenCalledWith(`/api/v1/applications/${APPLICATION}/notes`, expect.objectContaining({ method: "POST", body: { text: "ok" } })));
  });

  it("explica como ligar o Ollama quando ele não responde", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("Failed to fetch"));
    const user = userEvent.setup({ delay: null });
    renderChat();
    await configureOllama(user);
    await user.type(screen.getByRole("textbox", { name: "Mensagem" }), "oi");
    await user.click(screen.getByRole("button", { name: "Enviar" }));
    expect(await screen.findByText(/ollama serve/)).toBeTruthy();
  });
});
