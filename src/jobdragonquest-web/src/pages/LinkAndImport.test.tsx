import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "../api/client";
import { I18nProvider } from "../i18n/I18nContext";
import { BoardPage } from "./BoardPage";
import { JobsPage } from "./JobsPage";

const api = vi.hoisted(() => ({
  jobs: vi.fn(),
  similarJobs: vi.fn(),
  setFavorite: vi.fn(),
  createJob: vi.fn(),
  jobFromLink: vi.fn(),
  board: vi.fn(),
  profiles: vi.fn(),
  createApplication: vi.fn(),
  batchTransition: vi.fn(),
  importApplications: vi.fn(),
}));
vi.mock("../api/endpoints", () => ({ api }));

const wrap = (node: React.ReactNode) =>
  render(
    <I18nProvider initial="pt-BR">
      <MemoryRouter>{node}</MemoryRouter>
    </I18nProvider>,
  );

beforeEach(() => {
  Object.values(api).forEach((mock) => mock.mockReset());
  api.jobs.mockResolvedValue({ data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } });
  api.similarJobs.mockResolvedValue({ data: [] });
  api.board.mockResolvedValue({ data: [] });
  api.profiles.mockResolvedValue({ data: [] });
});

describe("vaga pelo link e importação por CSV (#114)", () => {
  it("preenche o formulário com o rascunho lido do link", async () => {
    api.jobFromLink.mockResolvedValue({
      data: {
        extractedFrom: "JSON_LD",
        job: {
          source: "LINK",
          url: "https://empresa.test/v/1",
          company: "Empresa Link",
          title: "Desenvolvedora Java",
          location: "Recife, PE",
          country: "BR",
          workMode: "REMOTE",
          seniority: null,
          description: "Java e Spring",
          requirements: [],
          languages: [],
        },
      },
    });
    const user = userEvent.setup({ delay: null });
    wrap(<JobsPage />);
    await user.click(await screen.findByRole("button", { name: "Cadastrar vaga" }));
    await user.type(screen.getByRole("textbox", { name: /^Ler a vaga de um link/ }), "https://empresa.test/v/1");
    await user.click(screen.getByRole("button", { name: "Preencher pelo link" }));
    await waitFor(() => expect(api.jobFromLink).toHaveBeenCalledWith("https://empresa.test/v/1"));
    expect(await screen.findByDisplayValue("Empresa Link")).toBeTruthy();
    expect(screen.getByDisplayValue("Desenvolvedora Java")).toBeTruthy();
    expect(screen.getByText(/dados estruturados da vaga/)).toBeTruthy();
  });

  it("mostra o motivo quando o link é bloqueado", async () => {
    api.jobFromLink.mockRejectedValue(new ApiError(400, "link_blocked", "x"));
    const user = userEvent.setup({ delay: null });
    wrap(<JobsPage />);
    await user.click(await screen.findByRole("button", { name: "Cadastrar vaga" }));
    await user.type(screen.getByRole("textbox", { name: /^Ler a vaga de um link/ }), "http://127.0.0.1/");
    await user.click(screen.getByRole("button", { name: "Preencher pelo link" }));
    expect(await screen.findByText(/use um endereço público/)).toBeTruthy();
  });

  it("importa o CSV e mostra o relatório com as linhas com problema", async () => {
    api.importApplications.mockResolvedValue({ data: { created: 2, duplicates: 1, errors: [{ line: 4, code: "csv_invalid_date" }] } });
    const user = userEvent.setup({ delay: null });
    wrap(<BoardPage />);
    const input = (await screen.findByText("Importar CSV")).querySelector("input")!;
    const file = new File(["empresa;cargo\nAcme;Dev\n"], "candidaturas.csv", { type: "text/csv" });
    await user.upload(input, file);
    await waitFor(() => expect(api.importApplications).toHaveBeenCalledWith("empresa;cargo\nAcme;Dev\n"));
    expect(await screen.findByText(/2 candidatura\(s\) importada\(s\), 1 já existia\(m\). Linhas com problema: 4 \(data inválida\)/)).toBeTruthy();
  });
});
