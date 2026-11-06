import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { ProfileEditorPage } from "./ProfileEditorPage";

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

describe("perfis (#13)", () => {
  it("cria um perfil com competências, cargos e modalidades", async () => {
    api.createProfile.mockResolvedValue({ data: { id: "novo-id" } });
    // Depois de salvar, o editor abre o perfil criado e o carrega da API.
    api.profile.mockResolvedValue({
      data: {
        id: "novo-id", name: "Desenvolvedora Java", objective: null, seniority: "JUNIOR", minMatchScore: 60,
        salaryExpectation: null, salaryCurrency: "BRL", availability: null, restrictions: null, preferredLanguage: "pt-BR",
        desiredTitles: ["Desenvolvedora Java", "Backend"], locations: [], workModes: ["REMOTE", "HYBRID"],
        skills: [{ name: "Java", level: 3, years: null }], languages: [], entries: [],
        createdAt: "2026-11-06T10:00:00Z", updatedAt: "2026-11-06T10:00:00Z", version: 0,
      },
    });
    const user = userEvent.setup({ delay: null });
    renderAt("/perfis/novo", [{ path: "/perfis/:id", element: <ProfileEditorPage /> }]);

    await user.type(screen.getByRole("textbox", { name: "Nome do perfil" }), "Desenvolvedora Java");
    await user.type(screen.getByRole("textbox", { name: /Cargos desejados/ }), "Desenvolvedora Java, Backend");
    await user.click(screen.getByRole("checkbox", { name: "Híbrido" }));
    await user.click(screen.getAllByRole("button", { name: "Adicionar" })[0]!);
    await user.type(screen.getByRole("textbox", { name: "Competência" }), "Java");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(api.createProfile).toHaveBeenCalledTimes(1));
    const body = api.createProfile.mock.calls[0]?.[0];
    expect(body.name).toBe("Desenvolvedora Java");
    expect(body.desiredTitles).toEqual(["Desenvolvedora Java", "Backend"]);
    expect(body.workModes).toEqual(["REMOTE", "HYBRID"]);
    expect(body.skills).toEqual([{ name: "Java", level: 3, years: null }]);
    await waitFor(() => expect(api.profile).toHaveBeenCalledWith("novo-id"));
    expect(await screen.findByRole("heading", { name: "Desenvolvedora Java" })).toBeTruthy();
  });
});
