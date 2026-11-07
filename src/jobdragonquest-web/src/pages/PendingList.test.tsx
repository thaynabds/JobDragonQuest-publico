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

describe("central de pendências (#17, #38)", () => {
  it("monta as frases do sistema em inglês", async () => {
    api.pending.mockResolvedValue({
      data: [{ type: "SOURCE_TO_CHECK", priority: "LOW", applicationId: null, eventId: null, sourceId: "s1", company: "LinkedIn", title: "Java", message: "Hora de conferir novas vagas em LinkedIn", dueAt: null, url: null, suggestedState: null, confidence: null, detail: null }],
    });
    render(
      <I18nProvider initial="en">
        <MemoryRouter>
          <PendingList />
        </MemoryRouter>
      </I18nProvider>,
    );
    expect(await screen.findByText("Time to check new jobs on LinkedIn")).toBeTruthy();
    expect(screen.queryByText("Hora de conferir novas vagas em LinkedIn")).toBeNull();
  });

  it("aceita uma sugestão e confere uma fonte assistida", async () => {
    api.pending.mockResolvedValue({
      data: [
        { type: "SUGGESTION_TO_REVIEW", priority: "MEDIUM", applicationId: "a1", eventId: "e1", sourceId: null, company: "Segsat", title: "Estágio", message: "Confirmar mudança", dueAt: null, url: null, suggestedState: "INTERVIEW_SCHEDULED", confidence: 0.55, detail: null },
        { type: "SOURCE_TO_CHECK", priority: "LOW", applicationId: null, eventId: null, sourceId: "s1", company: "LinkedIn", title: "Vagas Java", message: "Hora de conferir", dueAt: null, url: "https://linkedin.com/jobs", suggestedState: null, confidence: null, detail: null },
        { type: "TECH_TEST", priority: "HIGH", applicationId: "a2", eventId: null, sourceId: null, company: "Acme", title: "Dev", message: "Fazer o teste", dueAt: null, url: null, suggestedState: null, confidence: null, detail: "Fazer o teste até sexta" },
      ],
    });
    api.acceptSuggestion.mockResolvedValue({ data: {} });
    api.checkSource.mockResolvedValue({ data: {} });
    const user = userEvent.setup({ delay: null });
    renderAt("/", [{ path: "/", element: <PendingList /> }]);

    expect(await screen.findByText("Confirmar mudança para Entrevista agendada (confiança 55%)")).toBeTruthy();
    expect(screen.getByText("Hora de conferir novas vagas em LinkedIn")).toBeTruthy();
    expect(screen.getByText("Fazer o teste até sexta")).toBeTruthy();
    await user.click(await screen.findByRole("button", { name: "Aceitar" }));
    await waitFor(() => expect(api.acceptSuggestion).toHaveBeenCalledWith("a1", "e1"));
    await user.click(screen.getByRole("button", { name: "Marcar como conferida" }));
    await waitFor(() => expect(api.checkSource).toHaveBeenCalledWith("s1", { jobsRegistered: 0, note: null }));
    expect(screen.getAllByRole("link", { name: "Abrir" }).some((link) => link.getAttribute("href") === "https://linkedin.com/jobs")).toBe(true);
  });
});
