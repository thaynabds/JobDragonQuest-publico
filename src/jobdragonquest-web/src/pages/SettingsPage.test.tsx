import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { NotificationsProvider } from "../notifications/NotificationsContext";
import { parseLeads } from "./NotificationSettingsSection";
import { SettingsPage } from "./SettingsPage";

const mocks = vi.hoisted(() => ({
  recoveryStatus: vi.fn(() => Promise.resolve({ data: { remaining: 0 } })),
  notificationsDue: vi.fn(),
  notifications: vi.fn(),
  notificationPreferences: vi.fn(),
  updateNotificationPreferences: vi.fn(),
  markDelivered: vi.fn(),
  testNotification: vi.fn(),
  tokens: vi.fn(),
  createToken: vi.fn(),
  revokeToken: vi.fn(),
}));
vi.mock("../api/endpoints", () => ({ api: mocks }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => ({ user: { displayName: "Pessoa", email: "p@teste.dev" }, reloadUser: vi.fn() }) }));

const pref = (topic: string, extra: object = {}) => ({
  topic,
  enabled: true,
  popup: true,
  system: false,
  sound: false,
  leadMinutes: null,
  minScore: null,
  staleDays: null,
  ...extra,
});

const view = {
  settings: { quietEnabled: false, quietStart: "22:00", quietEnd: "08:00", quietAllowHigh: true, hideDetails: false },
  timeZone: "America/Recife",
  preferences: [
    { topic: "STAGE:SCREENING", group: "STAGE", key: "SCREENING", label: null, priority: "MEDIUM", supportsLead: false, supportsStaleDays: false, supportsMinScore: false, preference: pref("STAGE:SCREENING") },
    { topic: "INTERVIEW_REMINDER", group: "PENDING", key: "INTERVIEW_REMINDER", label: null, priority: "HIGH", supportsLead: true, supportsStaleDays: false, supportsMinScore: false, preference: pref("INTERVIEW_REMINDER", { leadMinutes: [1440, 60] }) },
    { topic: "NEW_JOBS:s1", group: "SOURCE", key: "s1", label: "Board Acme", priority: "MEDIUM", supportsLead: false, supportsStaleDays: false, supportsMinScore: true, preference: pref("NEW_JOBS:s1") },
  ],
};

describe("configurações de avisos", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.notificationsDue.mockResolvedValue({ data: [] });
    mocks.notifications.mockResolvedValue({ data: { items: [], unreadCount: 0, page: 0, size: 1, totalItems: 0 } });
    mocks.notificationPreferences.mockResolvedValue({ data: view });
    mocks.updateNotificationPreferences.mockImplementation((body: unknown) => Promise.resolve({ data: { ...view, ...(body as object) } }));
    mocks.tokens.mockResolvedValue({ data: [] });
  });

  it("converte a antecedência digitada", () => {
    expect(parseLeads("1440, 60")).toEqual([1440, 60]);
    expect(parseLeads(" ")).toBeNull();
  });

  it("edita etapa, antecedência, nota mínima e silêncio e salva tudo de uma vez", async () => {
    render(
      <MemoryRouter>
        <I18nProvider initial="pt-BR">
          <NotificationsProvider pollMs={60_000}>
            <SettingsPage />
          </NotificationsProvider>
        </I18nProvider>
      </MemoryRouter>,
    );
    const screening = await screen.findByRole("checkbox", { name: "Triagem: Avisar" });
    await userEvent.click(screening);
    expect((screen.getByRole("checkbox", { name: "Triagem: Pop-up" }) as HTMLInputElement).disabled).toBe(true);

    const lead = screen.getByRole("textbox", { name: "Lembrete de entrevista: Antecedência (min)" });
    await userEvent.clear(lead);
    await userEvent.type(lead, "120, 30", { delay: null });
    await userEvent.type(screen.getByRole("spinbutton", { name: "Board Acme: Nota mínima" }), "70", { delay: null });
    await userEvent.click(screen.getByRole("checkbox", { name: /Silenciar pop-ups/ }));
    await userEvent.click(screen.getAllByRole("button", { name: "Salvar" })[0]!);

    await waitFor(() => expect(mocks.updateNotificationPreferences).toHaveBeenCalled());
    const body = mocks.updateNotificationPreferences.mock.calls[0]![0] as { settings: { quietEnabled: boolean }; preferences: { topic: string; enabled: boolean; leadMinutes: number[] | null; minScore: number | null }[] };
    expect(body.settings.quietEnabled).toBe(true);
    expect(body.preferences.find((p) => p.topic === "STAGE:SCREENING")!.enabled).toBe(false);
    expect(body.preferences.find((p) => p.topic === "INTERVIEW_REMINDER")!.leadMinutes).toEqual([120, 30]);
    expect(body.preferences.find((p) => p.topic === "NEW_JOBS:s1")!.minScore).toBe(70);
    expect(await screen.findByText("Salvo.")).toBeTruthy();
  });
});
