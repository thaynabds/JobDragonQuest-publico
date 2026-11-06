import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { CatalogEntry } from "../api/types";
import { I18nProvider } from "../i18n/I18nContext";
import { SourcesPage, toSourceInputs } from "./SourcesPage";

const catalog: CatalogEntry[] = [
  { kind: "GREENHOUSE", label: "Greenhouse", mode: "AUTOMATIC", needsIdentifier: true },
  { kind: "LINKEDIN", label: "LinkedIn", mode: "ASSISTED", needsIdentifier: false },
  { kind: "INSTAGRAM", label: "Instagram", mode: "ASSISTED", needsIdentifier: false },
];

const mocks = vi.hoisted(() => ({
  createSources: vi.fn(),
  updateSources: vi.fn(),
  sources: vi.fn(),
}));

vi.mock("../api/endpoints", () => ({
  api: {
    sourceCatalog: () => Promise.resolve({ data: catalog }),
    profiles: () =>
      Promise.resolve({
        data: [
          { id: "p1", name: "Java", seniority: "JUNIOR", desiredTitles: [], skillCount: 2 },
          { id: "p2", name: "Estágio", seniority: "INTERN", desiredTitles: [], skillCount: 1 },
        ],
      }),
    sources: mocks.sources,
    createSources: mocks.createSources,
    updateSources: mocks.updateSources,
    sourceRuns: () => Promise.resolve({ data: [] }),
  },
}));

const source = (id: string, name: string) => ({
  id,
  name,
  kind: "LINKEDIN",
  kindLabel: "LinkedIn",
  collectionMode: "ASSISTED",
  identifier: null,
  url: "https://linkedin.com/jobs",
  openUrl: "https://linkedin.com/jobs",
  frequencyMinutes: 120,
  enabled: true,
  profileIds: ["p1"],
  keywords: [],
  nextRunAt: "2026-11-06T12:00:00Z",
  lastRunAt: null,
  lastStatus: null,
  lastMessage: null,
  lastFound: null,
  consecutiveFailures: 0,
  due: true,
  running: false,
  createdAt: "2026-11-06T10:00:00Z",
  version: 0,
});

beforeEach(() => {
  mocks.sources.mockResolvedValue({ data: [] });
  mocks.createSources.mockResolvedValue({ data: { created: [{}, {}, {}], duplicates: [] } });
  mocks.updateSources.mockResolvedValue({ data: [] });
});

describe("fontes vigiadas", () => {
  it("ATS levam identificador e as demais levam URL; linhas vazias são ignoradas", () => {
    expect(
      toSourceInputs(
        [
          { name: "Nubank", kind: "GREENHOUSE", target: " nubank " },
          { name: "", kind: "LINKEDIN", target: "https://linkedin.com/jobs/search?keywords=java" },
          { name: "", kind: "INSTAGRAM", target: "" },
        ],
        catalog,
      ),
    ).toEqual([
      { name: "Nubank", kind: "GREENHOUSE", identifier: "nubank" },
      { name: "https://linkedin.com/jobs/search?keywords=java", kind: "LINKEDIN", url: "https://linkedin.com/jobs/search?keywords=java" },
    ]);
  });

  it("cadastra várias fontes de uma vez ligadas a dois perfis", async () => {
    const user = userEvent.setup({ delay: null });
    render(
      <I18nProvider initial="pt-BR">
        <SourcesPage />
      </I18nProvider>,
    );
    await screen.findByText("Estágio");

    const targets = screen.getAllByRole("textbox", { name: "Board ou endereço" });
    await user.type(targets[0]!, "nubank");
    await user.type(targets[1]!, "https://www.linkedin.com/jobs/search/?keywords=java");
    await user.click(screen.getByRole("button", { name: "Adicionar outra fonte" }));
    const kinds = screen.getAllByRole("combobox", { name: "Tipo" });
    await user.selectOptions(kinds[2]!, "INSTAGRAM");
    await user.type(screen.getAllByRole("textbox", { name: "Board ou endereço" })[2]!, "https://www.instagram.com/explore/tags/vagasti/");
    await user.click(screen.getByRole("checkbox", { name: "Java" }));
    await user.click(screen.getByRole("checkbox", { name: "Estágio" }));
    await user.type(screen.getByRole("textbox", { name: /Palavras-chave/ }), "Java, Estágio");
    await user.click(screen.getByRole("button", { name: "Salvar fontes" }));

    await waitFor(() => expect(mocks.createSources).toHaveBeenCalledTimes(1));
    expect(mocks.createSources.mock.calls[0]?.[0]).toEqual({
      sources: [
        { name: "nubank", kind: "GREENHOUSE", identifier: "nubank" },
        { name: "https://www.linkedin.com/jobs/search/?keywords=java", kind: "LINKEDIN", url: "https://www.linkedin.com/jobs/search/?keywords=java" },
        { name: "https://www.instagram.com/explore/tags/vagasti/", kind: "INSTAGRAM", url: "https://www.instagram.com/explore/tags/vagasti/" },
      ],
      profileIds: ["p1", "p2"],
      frequencyMinutes: 360,
      keywords: ["Java", "Estágio"],
    });
    expect(await screen.findByText("3 fonte(s) criada(s), 0 já existia(m).")).toBeTruthy();
  });

  it("pausa várias fontes selecionadas de uma vez e mostra frequência fora da lista", async () => {
    mocks.sources.mockResolvedValue({ data: [source("s1", "Busca Java"), source("s2", "Busca Estágio")] });
    const user = userEvent.setup({ delay: null });
    render(
      <I18nProvider initial="pt-BR">
        <SourcesPage />
      </I18nProvider>,
    );
    await screen.findByText("Busca Java");
    expect(screen.getAllByText("120 min").length).toBe(2);

    await user.click(screen.getByRole("checkbox", { name: "Todos" }));
    const bar = screen.getByRole("region", { name: "2 selecionada(s)" });
    await user.click(within(bar).getByRole("button", { name: "Pausar" }));

    await waitFor(() => expect(mocks.updateSources).toHaveBeenCalledWith({ sourceIds: ["s1", "s2"], enabled: false }));
  });
});
