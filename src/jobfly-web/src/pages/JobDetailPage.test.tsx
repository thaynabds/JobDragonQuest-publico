import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { JobDetailPage } from "./JobDetailPage";

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

describe("compatibilidade (#15)", () => {
  it("mostra nota, recomendação, impedimentos e motivos por perfil", async () => {
    api.job.mockResolvedValue({
      data: { id: "j1", title: "Dev Java Júnior", company: "Acme", location: "Recife", workMode: "HYBRID", seniority: "JUNIOR", url: "javascript:alert(1)", description: "Java", source: "MANUAL", foundAt: "2026-11-06T10:00:00Z" },
    });
    api.matches.mockResolvedValue({
      data: [
        {
          profileId: "p1", profileName: "Java", score: 70, recommendation: "APPLY", summary: "Compatível", titleAffinity: true,
          blockers: [], strengths: ["Domínio de Java (nível 4)"], warnings: [],
          requirements: [{ kind: "MANDATORY", text: "Java", skill: "java", outcome: "MET", reason: "Java nível 4" }],
        },
        {
          profileId: "p2", profileName: "Presencial", score: 100, recommendation: "SKIP", summary: "Não recomendada", titleAffinity: false,
          blockers: [{ code: "WORK_MODE", message: "A vaga é HYBRID e o perfil aceita [ONSITE]" }], strengths: [], warnings: [], requirements: [],
        },
      ],
    });
    renderAt("/vagas/j1", [{ path: "/vagas/:id", element: <JobDetailPage /> }]);

    expect(await screen.findByText("70%")).toBeTruthy();
    expect(screen.getByText("Candidatar")).toBeTruthy();
    expect(screen.getByText("A vaga é HYBRID e o perfil aceita [ONSITE]")).toBeTruthy();
    expect(screen.getByText(/Java nível 4/)).toBeTruthy();
    // Link com javascript: nunca vira âncora.
    expect(screen.queryByRole("link", { name: /Abrir vaga/ })).toBeNull();
  });
});
