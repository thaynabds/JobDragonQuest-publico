import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { ReviewQueuePage } from "./ReviewQueuePage";

const api = vi.hoisted(() => ({ applications: vi.fn(), batchTransition: vi.fn() }));
vi.mock("../api/endpoints", () => ({ api }));

const item = (id: string, title: string) => ({
  id,
  title,
  company: "Empresa",
  origin: "AUTOMATION",
  platform: "Greenhouse",
  state: "RECOMMENDED",
  matchScore: 80,
  notes: "Compatível: nota 80",
  appliedAt: null,
  createdAt: "2026-11-07T10:00:00Z",
  stale: false,
  nextAction: null,
});

const renderPage = () =>
  render(
    <I18nProvider initial="pt-BR">
      <MemoryRouter>
        <ReviewQueuePage />
      </MemoryRouter>
    </I18nProvider>,
  );

beforeEach(() => Object.values(api).forEach((mock) => mock.mockReset()));

describe("fila de revisão (#112)", () => {
  it("lista as recomendadas, aprova uma e descarta outras em lote", async () => {
    api.applications.mockResolvedValue({
      data: { content: [item("a1", "Dev A"), item("a2", "Dev B"), item("a3", "Dev C")], page: 0, size: 100, totalElements: 3, totalPages: 1 },
    });
    api.batchTransition.mockResolvedValue({ data: { updated: 1, failed: [] } });
    const user = userEvent.setup({ delay: null });
    renderPage();

    expect(api.applications).toHaveBeenCalledWith({ state: "RECOMMENDED", size: 100 });
    await user.click(await screen.findByRole("button", { name: "Aprovar Dev A" }));
    await waitFor(() => expect(api.batchTransition).toHaveBeenCalledWith({ ids: ["a1"], toState: "PREPARED", evidence: "Aprovada na fila de revisão" }));

    await user.click(screen.getByRole("checkbox", { name: "Selecionar Dev B" }));
    await user.click(screen.getByRole("checkbox", { name: "Selecionar Dev C" }));
    await user.click(screen.getByRole("button", { name: "Descartar selecionadas" }));
    await waitFor(() =>
      expect(api.batchTransition).toHaveBeenLastCalledWith({ ids: ["a2", "a3"], toState: "CLOSED", evidence: "Descartada na fila de revisão" }),
    );
  });

  it("mostra a fila vazia", async () => {
    api.applications.mockResolvedValue({ data: { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 } });
    renderPage();
    expect(await screen.findByText("Nada para revisar agora.")).toBeTruthy();
  });
});
