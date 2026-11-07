import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { NotificationItem } from "../api/types";
import { I18nProvider } from "../i18n/I18nContext";
import { LOCALES } from "../i18n/messages";
import { Bell } from "./Bell";
import { NotificationsProvider } from "./NotificationsContext";
import { notificationLink, notificationText } from "./text";
import { AUTO_DISMISS_MS, Toasts } from "./Toasts";

it("pop-ups comuns somem em 12 segundos por padrão", () => expect(AUTO_DISMISS_MS).toBe(12_000));

const mocks = vi.hoisted(() => ({
  notificationsDue: vi.fn(),
  notifications: vi.fn(),
  notificationPreferences: vi.fn(),
  markDelivered: vi.fn(),
  markRead: vi.fn(),
  markAllRead: vi.fn(),
  snooze: vi.fn(),
  testNotification: vi.fn(),
}));
vi.mock("../api/endpoints", () => ({ api: mocks }));

function item(overrides: Partial<NotificationItem>): NotificationItem {
  return {
    id: "n1",
    topic: "STAGE:INTERVIEW_SCHEDULED",
    type: "STAGE_CHANGED",
    priority: "HIGH",
    applicationId: "a1",
    sourceId: null,
    state: "INTERVIEW_SCHEDULED",
    company: "Acme",
    title: "Dev Java",
    detail: null,
    message: "Acme (Dev Java): etapa mudou para Entrevista agendada",
    dueAt: null,
    itemCount: null,
    leadMinutes: null,
    popup: true,
    system: true,
    sound: false,
    quiet: false,
    createdAt: "2026-11-06T12:00:00Z",
    deliveredAt: null,
    readAt: null,
    snoozedUntil: null,
    ...overrides,
  };
}

const t = (locale: keyof typeof LOCALES) => (key: string, values: Record<string, string | number> = {}) =>
  (LOCALES[locale] as Record<string, string>)[key]!.replace(/\{(\w+)\}/g, (_, name: string) => String(values[name]));
const label = (_prefix: string, value: string | null | undefined) => value ?? "";
const formatDate = (iso: string | null | undefined) => iso ?? "";

describe("texto dos avisos", () => {
  it("monta a frase no idioma escolhido pelos campos estruturados", () => {
    const stage = item({});
    expect(notificationText(stage, t("pt-BR") as never, label, formatDate)).toBe("Acme (Dev Java): etapa mudou para INTERVIEW_SCHEDULED");
    expect(notificationText(stage, t("en") as never, label, formatDate)).toBe("Acme (Dev Java): stage changed to INTERVIEW_SCHEDULED");
    expect(notificationText(item({ type: "NEW_JOBS", itemCount: 3, title: "Board", company: null, applicationId: null }), t("es") as never, label, formatDate)).toBe(
      "3 vacante(s) nueva(s) en Board",
    );
    expect(notificationText(item({ type: "ACTION_REQUIRED", detail: "Enviar portfólio" }), t("pt-BR") as never, label, formatDate)).toBe(
      "Acme (Dev Java): Enviar portfólio",
    );
  });

  it("leva para a candidatura ou para as vagas da fonte, com o id codificado", () => {
    expect(notificationLink(item({ applicationId: "a/1" }))).toBe("/candidaturas/a%2F1");
    expect(notificationLink(item({ applicationId: null, type: "NEW_JOBS", sourceId: "s1" }))).toBe("/vagas?sourceId=s1");
    expect(notificationLink(item({ applicationId: null, type: "TEST" }))).toBeNull();
  });
});

function renderProvider(children: React.ReactNode) {
  return render(
    <MemoryRouter>
      <I18nProvider initial="pt-BR">
        <NotificationsProvider pollMs={60_000}>{children}</NotificationsProvider>
      </I18nProvider>
    </MemoryRouter>,
  );
}

describe("pop-ups e notificações do sistema", () => {
  const systemCalls: { title: string; body: string }[] = [];

  beforeEach(() => {
    systemCalls.length = 0;
    class FakeNotification {
      static permission = "granted";
      static requestPermission = vi.fn().mockResolvedValue("granted");
      onclick: (() => void) | null = null;
      constructor(title: string, options: { body: string }) {
        systemCalls.push({ title, body: options.body });
      }
      close() {}
    }
    vi.stubGlobal("Notification", FakeNotification);
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.notifications.mockResolvedValue({ data: { items: [], unreadCount: 2, page: 0, size: 1, totalItems: 2 } });
    mocks.notificationPreferences.mockResolvedValue({ data: { settings: { hideDetails: false }, timeZone: "UTC", preferences: [] } });
    mocks.markDelivered.mockResolvedValue({ data: { count: 2 } });
    mocks.markRead.mockResolvedValue({ data: {} });
    mocks.snooze.mockResolvedValue({ data: {} });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("mostra pop-up e notificação do sistema conforme os canais e marca como entregues", async () => {
    mocks.notificationsDue.mockResolvedValue({
      data: [item({}), item({ id: "n2", popup: false, system: false, priority: "LOW", type: "NO_UPDATE", state: "SUBMITTED" })],
    });
    renderProvider(<Toasts />);
    expect(await screen.findByRole("alert")).toBeTruthy();
    expect(screen.getByText("Acme (Dev Java): etapa mudou para Entrevista agendada")).toBeTruthy();
    await waitFor(() => expect(mocks.markDelivered).toHaveBeenCalledWith(["n1", "n2"]));
    expect(systemCalls).toEqual([{ title: "JobDragonQuest · Etapa atualizada", body: "Acme (Dev Java): etapa mudou para Entrevista agendada" }]);
    expect(screen.queryByText(/sem atualização/i)).toBeNull();
  });

  it("esconde empresa e cargo na notificação do sistema quando configurado", async () => {
    mocks.notificationPreferences.mockResolvedValue({ data: { settings: { hideDetails: true }, timeZone: "UTC", preferences: [] } });
    let resolveDue: (value: unknown) => void = () => undefined;
    mocks.notificationsDue.mockReturnValue(new Promise((resolve) => (resolveDue = resolve)));
    renderProvider(<Toasts />);
    await waitFor(() => expect(mocks.notificationPreferences).toHaveBeenCalled());
    await act(async () => {
      await Promise.resolve();
      resolveDue({ data: [item({ popup: false })] });
    });
    await waitFor(() => expect(systemCalls).toHaveLength(1));
    expect(systemCalls[0]).toEqual({ title: "JobDragonQuest", body: "Você tem um aviso novo no JobDragonQuest." });
  });

  it("prioridade média some sozinha e alta fica até fechar; adiar chama a API", async () => {
    mocks.notificationsDue.mockResolvedValue({
      data: [item({ id: "high" }), item({ id: "medium", priority: "MEDIUM", type: "DEADLINE_REMINDER", dueAt: "2026-11-07T10:00:00Z", system: false })],
    });
    renderProvider(<Toasts autoDismissMs={300} />);
    expect(await screen.findByRole("alert")).toBeTruthy();
    expect(screen.getByRole("status")).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole("status")).toBeNull(), { timeout: 3000 });
    expect(screen.getByRole("alert")).toBeTruthy();
    await userEvent.click(screen.getByRole("button", { name: "Adiar 1 h" }));
    expect(mocks.snooze).toHaveBeenCalledWith("high", 60);
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("o sino mostra o contador e marca todos como lidos", async () => {
    mocks.notificationsDue.mockResolvedValue({ data: [] });
    mocks.markAllRead.mockResolvedValue({ data: { count: 2 } });
    renderProvider(<Bell />);
    const bell = await screen.findByRole("button", { name: "Avisos: 2 não lido(s)" });
    mocks.notifications.mockResolvedValue({ data: { items: [item({ deliveredAt: "x" })], unreadCount: 2, page: 0, size: 20, totalItems: 1 } });
    await userEvent.click(bell);
    expect(await screen.findByText("Acme (Dev Java): etapa mudou para Entrevista agendada")).toBeTruthy();
    await userEvent.click(screen.getByRole("button", { name: "Marcar todos como lidos" }));
    await waitFor(() => expect(mocks.markAllRead).toHaveBeenCalled());
  });

  it("com o sino aberto, a lista acompanha um aviso lido pelo pop-up", async () => {
    mocks.notificationsDue.mockResolvedValue({ data: [item({ id: "p1" })] });
    mocks.notifications.mockResolvedValue({ data: { items: [item({ id: "p1" })], unreadCount: 1, page: 0, size: 20, totalItems: 1 } });
    renderProvider(
      <>
        <Bell />
        <Toasts />
      </>,
    );
    await userEvent.click(await screen.findByRole("button", { name: "Avisos: 1 não lido(s)" }));
    await waitFor(() => expect(screen.getAllByRole("button", { name: "Marcar como lido" }).length).toBe(2));
    mocks.notifications.mockResolvedValue({ data: { items: [item({ id: "p1", readAt: "2026-11-07T01:00:00Z" })], unreadCount: 0, page: 0, size: 20, totalItems: 1 } });
    await userEvent.click(within(screen.getByRole("alert")).getByRole("button", { name: "Marcar como lido" }));
    await waitFor(() => expect(screen.queryAllByRole("button", { name: "Marcar como lido" })).toHaveLength(0));
  });
});
