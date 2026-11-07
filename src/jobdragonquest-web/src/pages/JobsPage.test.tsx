import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { JobsPage } from "./JobsPage";

const api = vi.hoisted(() => ({
  jobs: vi.fn(),
  setFavorite: vi.fn(),
  similarJobs: vi.fn(),
  createJob: vi.fn(),
}));
vi.mock("../api/endpoints", () => ({ api }));

const summary = (id: string, title: string, favorite = false) => ({
  id,
  title,
  company: "Empresa",
  location: null,
  workMode: null,
  status: "OPEN",
  source: "MANUAL",
  url: null,
  foundAt: "2026-11-07T10:00:00Z",
  watchedSourceId: null,
  favorite,
});

const page = (content: unknown[]) => ({
  data: {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
  },
});

const renderPage = () =>
  render(
    <I18nProvider initial="pt-BR">
      <MemoryRouter>
        <JobsPage />
      </MemoryRouter>
    </I18nProvider>,
  );

beforeEach(() => {
  Object.values(api).forEach((mock) => mock.mockReset());
});

describe("favoritas e vagas parecidas (#110)", () => {
  it("favorita uma vaga e mostra as parecidas com explicação", async () => {
    api.jobs.mockResolvedValue(page([summary("j1", "Desenvolvedora Java")]));
    api.similarJobs.mockResolvedValue({ data: [] });
    api.setFavorite.mockResolvedValue({
      data: summary("j1", "Desenvolvedora Java", true),
    });
    const user = userEvent.setup({ delay: null });
    renderPage();

    const star = await screen.findByRole("button", {
      name: "Favoritar Desenvolvedora Java",
    });
    expect(star.getAttribute("aria-pressed")).toBe("false");
    expect(await screen.findByText(/Favorite vagas na lista/)).toBeTruthy();

    api.similarJobs.mockResolvedValue({
      data: [
        {
          job: summary("j2", "Desenvolvedor Java Pleno"),
          score: 58,
          basisJobId: "j1",
          basisTitle: "Desenvolvedora Java",
          sharedWords: ["desenvolvedor", "java"],
          sharedSkills: ["java"],
        },
      ],
    });
    await user.click(star);
    await waitFor(() => expect(api.setFavorite).toHaveBeenCalledWith("j1", true));
    expect(await screen.findByText("58% parecida")).toBeTruthy();
    expect(screen.getByText(/palavras em comum: desenvolvedor, java/)).toBeTruthy();
    expect(screen.getByRole("link", { name: "Desenvolvedor Java Pleno" }).getAttribute("href")).toBe("/vagas/j2");
  });

  it("troca a base, ajusta o mínimo e filtra só as favoritas", async () => {
    api.jobs.mockResolvedValue(page([summary("j1", "Dev", true)]));
    api.similarJobs.mockResolvedValue({ data: [] });
    const user = userEvent.setup({ delay: null });
    renderPage();
    await screen.findByRole("button", { name: "Tirar Dev dos favoritos" });

    await user.selectOptions(screen.getByRole("combobox", { name: "Parecidas com" }), "APPLIED");
    await waitFor(() =>
      expect(api.similarJobs).toHaveBeenLastCalledWith({
        basis: "APPLIED",
        minScore: 30,
        limit: 10,
      }),
    );
    expect(await screen.findByText(/últimas candidaturas enviadas/)).toBeTruthy();

    await user.click(screen.getByRole("checkbox", { name: "Mostrar só as favoritas" }));
    await waitFor(() =>
      expect(api.jobs).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        favorite: true,
      }),
    );
  });
});
