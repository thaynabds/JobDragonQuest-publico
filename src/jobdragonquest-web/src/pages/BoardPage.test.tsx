import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { BoardPage } from "./BoardPage";

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
  batchTransition: vi.fn(),
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
  api.profiles.mockResolvedValue({
    data: [
      {
        id: "p1",
        name: "Java",
        seniority: "JUNIOR",
        desiredTitles: [],
        skillCount: 1,
      },
    ],
  });
});

describe("candidaturas (#16)", () => {
  it("registra uma candidatura feita no LinkedIn com chave de idempotência", async () => {
    api.board.mockResolvedValue({
      data: [{ state: "SUBMITTED", count: 0, cards: [] }],
    });
    api.createApplication.mockResolvedValue({
      data: { id: "a1" },
      headers: new Headers(),
    });
    const user = userEvent.setup({ delay: null });
    renderAt("/candidaturas", [{ path: "/candidaturas", element: <BoardPage /> }]);

    await user.click(
      await screen.findByRole("button", {
        name: "Registrar candidatura externa",
      }),
    );
    await user.type(screen.getByRole("textbox", { name: "Empresa" }), "Sensedia");
    await user.type(screen.getByRole("textbox", { name: "Cargo" }), "Desenvolvedor Java Júnior");
    await user.type(screen.getByRole("textbox", { name: "Link da vaga ou da candidatura" }), "https://www.linkedin.com/jobs/view/4486065865/");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(api.createApplication).toHaveBeenCalledTimes(1));
    const [body, key] = api.createApplication.mock.calls[0]!;
    expect(body).toMatchObject({
      origin: "MANUAL",
      platform: "LinkedIn",
      company: "Sensedia",
      state: "SUBMITTED",
    });
    expect(key).toMatch(/^ext-[A-Za-z0-9_-]+$/);
    expect(await screen.findByText("destino")).toBeTruthy();
  });

  it("seleciona várias candidaturas e muda a etapa de todas de uma vez (#106)", async () => {
    api.board.mockClear();
    api.batchTransition.mockClear();
    const card = (id: string, title: string) => ({
      id,
      title,
      company: "Lote SA",
      origin: "MANUAL",
      platform: "LinkedIn",
      state: "SUBMITTED",
      appliedAt: null,
      createdAt: "2026-11-07T10:00:00Z",
      matchScore: null,
      stale: false,
      nextAction: null,
    });
    api.board.mockResolvedValue({
      data: [
        {
          state: "SUBMITTED",
          count: 2,
          cards: [card("a1", "Dev A"), card("a2", "Dev B")],
        },
      ],
    });
    api.batchTransition.mockResolvedValue({ data: { updated: 2, failed: [] } });
    const user = userEvent.setup({ delay: null });
    renderAt("/candidaturas", [{ path: "/candidaturas", element: <BoardPage /> }]);

    await user.click(await screen.findByRole("checkbox", { name: "Selecionar Dev A" }));
    await user.click(screen.getByRole("checkbox", { name: "Selecionar Dev B" }));
    expect(screen.getByText("2 selecionada(s)")).toBeTruthy();
    await user.selectOptions(screen.getByRole("combobox", { name: "Mover para" }), "INTERVIEW_SCHEDULED");
    await user.type(screen.getByRole("textbox", { name: "Motivo (fica no histórico)" }), "Convites recebidos");
    await user.click(screen.getByRole("button", { name: "Mover selecionadas" }));

    await waitFor(() => expect(api.batchTransition).toHaveBeenCalledTimes(1));
    expect(api.batchTransition.mock.calls[0]![0]).toEqual({
      ids: ["a1", "a2"],
      toState: "INTERVIEW_SCHEDULED",
      evidence: "Convites recebidos",
    });
    expect(await screen.findByText("2 candidatura(s) atualizada(s). 0 não mudaram.")).toBeTruthy();
    await waitFor(() => expect(api.board).toHaveBeenCalledTimes(2));
    expect(screen.queryByText("2 selecionada(s)")).toBeNull();
  });

  it("descarta as selecionadas com motivo padrão", async () => {
    api.board.mockResolvedValue({
      data: [
        {
          state: "SUBMITTED",
          count: 1,
          cards: [
            {
              id: "a9",
              title: "Dev Z",
              company: "Z",
              origin: "MANUAL",
              platform: null,
              state: "SUBMITTED",
              appliedAt: null,
              createdAt: "2026-11-07T10:00:00Z",
              matchScore: null,
              stale: false,
              nextAction: null,
            },
          ],
        },
      ],
    });
    api.batchTransition.mockResolvedValue({ data: { updated: 1, failed: [] } });
    const user = userEvent.setup({ delay: null });
    renderAt("/candidaturas", [{ path: "/candidaturas", element: <BoardPage /> }]);
    await user.click(await screen.findByRole("checkbox", { name: "Selecionar Dev Z" }));
    await user.click(screen.getByRole("button", { name: "Descartar selecionadas" }));
    await waitFor(() =>
      expect(api.batchTransition).toHaveBeenCalledWith({
        ids: ["a9"],
        toState: "CLOSED",
        evidence: "Descartada em lote pelo quadro",
      }),
    );
  });
});
