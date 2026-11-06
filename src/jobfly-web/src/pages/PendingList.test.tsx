import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { PendingList } from "./PendingList";

const api = vi.hoisted(() => ({
  pending: vi.fn(),
  acceptSuggestion: vi.fn(),
  checkSource: vi.fn(),
  dismissSuggestion: vi.fn(),
  createProfile: vi.fn(),
  profile: vi.fn(),
  profiles: vi.fn(),
  board: vi.fn(),
  createApplication: vi.fn(),
  job: vi.fn(),
  matches: vi.fn(),
  application: vi.fn(),
}));
vi.mock("../api/endpoints", () => ({ api }));

const renderAt = (path: string, routes: { path: string; element: React.ReactNode }[]) =>
  render(
    <I18nProvider initial="pt-BR">
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          {routes.map((route) => (
            <Route key={route.path} path={route.path} element={route.element} />
          ))}
          <Route path="*" element={<p>destino</p>} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  );

beforeEach(() => {
  api.profiles.mockResolvedValue({ data: [{ id: "p1", name: "Java", seniority: "JUNIOR", desiredTitles: [], skillCount: 1 }] });
});

describe("central de pendências (#17)", () => {
  it("aceita uma sugestão e confere uma fonte assistida", async () => {
    api.pending.mockResolvedValue({
      data: [
        { type: "SUGGESTION_TO_REVIEW", priority: "MEDIUM", applicationId: "a1", eventId: "e1", sourceId: null, company: "Segsat", title: "Estágio", message: "Confirmar mudança", dueAt: null, url: null },
        { type: "SOURCE_TO_CHECK", priority: "LOW", applicationId: null, eventId: null, sourceId: "s1", company: "LinkedIn", title: "Vagas Java", message: "Hora de conferir", dueAt: null, url: "https://linkedin.com/jobs" },
      ],
    });
    api.acceptSuggestion.mockResolvedValue({ data: {} });
    api.checkSource.mockResolvedValue({ data: {} });
    const user = userEvent.setup({ delay: null });
    renderAt("/", [{ path: "/", element: <PendingList /> }]);

    await user.click(await screen.findByRole("button", { name: "Aceitar" }));
    await waitFor(() => expect(api.acceptSuggestion).toHaveBeenCalledWith("a1", "e1"));
    await user.click(screen.getByRole("button", { name: "Marcar como conferida" }));
    await waitFor(() => expect(api.checkSource).toHaveBeenCalledWith("s1", { jobsRegistered: 0, note: null }));
    expect(screen.getByRole("link", { name: "Abrir" }).getAttribute("href")).toBe("https://linkedin.com/jobs");
  });
});
