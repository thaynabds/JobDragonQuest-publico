import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Profile } from "../api/types";
import { I18nProvider } from "../i18n/I18nContext";
import { countryCode, languageName } from "../utils/places";
import { ProfileEditorPage, toInput } from "./ProfileEditorPage";

const api = vi.hoisted(() => ({
  createProfile: vi.fn(),
  updateProfile: vi.fn(),
  deleteProfile: vi.fn(),
  profile: vi.fn(),
  profileDocuments: vi.fn(),
}));
vi.mock("../api/endpoints", () => ({ api }));

const renderAt = (path: string) =>
  render(
    <I18nProvider initial="pt-BR">
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/perfis/:id" element={<ProfileEditorPage />} />
          <Route path="*" element={<p>destino</p>} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  );

const saved: Profile = {
  id: "p1",
  name: "Desenvolvedora Java",
  objective: null,
  seniority: "SPECIALIST",
  minMatchScore: 60,
  automationMode: "REVIEW",
  salaryExpectation: 6000,
  salaryCurrency: "BRL",
  availability: null,
  restrictions: null,
  preferredLanguage: "pt-BR",
  desiredTitles: ["Desenvolvedora Java"],
  locations: ["Recife"],
  workModes: ["REMOTE"],
  entries: [],
  headline: "Back-end Java | Spring Boot",
  summary: null,
  currentTitle: null,
  city: "Recife",
  stateRegion: "PE",
  country: "BR",
  requiresSponsorship: null,
  willingToRelocate: true,
  travelAvailability: null,
  noticePeriodDays: null,
  availableFrom: null,
  currentSalary: null,
  salaryPeriod: "MONTHLY",
  linkedinUrl: null,
  githubUrl: null,
  portfolioUrl: null,
  websiteUrl: null,
  coverLetter: null,
  disability: null,
  disabilityDetails: null,
  workAuthorization: ["BR", "PT"],
  employmentTypes: ["CLT"],
  excludedCompanies: [],
  includeKeywords: [],
  excludeKeywords: [],
  industries: [],
  answers: [],
  skills: [{ name: "Java", level: 4, years: 1, months: 6, days: 10, totalYears: 1.5 }],
  languages: [{ code: "en", level: "B2" }],
  createdAt: "2026-11-07T10:00:00Z",
  updatedAt: "2026-11-07T10:00:00Z",
  version: 3,
};

beforeEach(() => {
  Object.values(api).forEach((mock) => mock.mockReset());
  api.profileDocuments.mockResolvedValue({ data: [] });
});

describe("nomes de idiomas e países", () => {
  it("mostra o idioma por extenso no idioma do painel e reconhece países pelo nome", () => {
    expect(languageName("en", "pt-BR")).toBe("Inglês");
    expect(languageName("es", "en")).toBe("Spanish");
    expect(languageName("bzs", "pt-BR")).toContain("Libras");
    expect(countryCode("Brasil", "pt-BR")).toBe("BR");
    expect(countryCode("portugal", "en")).toBe("PT");
    expect(countryCode("pt", "pt-BR")).toBe("PT");
    expect(countryCode("Atlântida", "pt-BR")).toBeNull();
  });

  it("converte o perfil salvo para edição, com idiomas por nome", () => {
    const input = toInput(saved, "pt-BR");
    expect(input.languages).toEqual([{ name: "Inglês", level: "B2" }]);
    expect(input.skills).toEqual([{ name: "Java", level: 4, years: 1, months: 6, days: 10 }]);
    expect(input).not.toHaveProperty("id");
  });
});

describe("perfil completo", () => {
  it("cria um perfil com senioridade, tempo de competência em anos, meses e dias, idioma por nome e preferências", async () => {
    api.createProfile.mockResolvedValue({ data: { id: "novo-id" } });
    api.profile.mockResolvedValue({ data: { ...saved, id: "novo-id" } });
    const user = userEvent.setup({ delay: null });
    renderAt("/perfis/novo");

    await user.type(screen.getByRole("textbox", { name: /^Nome do perfil/ }), "Desenvolvedora Java");
    await user.selectOptions(screen.getByRole("combobox", { name: "Senioridade" }), "SPECIALIST");
    await user.type(screen.getByRole("textbox", { name: /^Título profissional/ }), "Back-end Java");
    await user.type(screen.getByRole("combobox", { name: "País" }), "Brasil");
    await user.type(screen.getByRole("textbox", { name: /^Países onde pode trabalhar/ }), "Brasil, Portugal");
    await user.click(screen.getByRole("checkbox", { name: "CLT (efetivo)" }));
    await user.click(screen.getByRole("checkbox", { name: "Híbrido" }));

    const add = () => screen.getAllByRole("button", { name: "Adicionar" });
    await user.click(add()[0]!);
    await user.type(screen.getByRole("textbox", { name: "Competência" }), "Java");
    await user.type(screen.getByRole("spinbutton", { name: "Anos" }), "1");
    await user.type(screen.getByRole("spinbutton", { name: "Meses" }), "6");
    await user.type(screen.getByRole("spinbutton", { name: "Dias" }), "10");

    await user.click(add()[1]!);
    await user.type(screen.getByRole("combobox", { name: "Idioma" }), "Inglês");
    await user.selectOptions(screen.getByRole("combobox", { name: "Nível" }), "B2");

    await user.click(add()[3]!);
    await user.type(screen.getByRole("textbox", { name: "Pergunta" }), "Por que quer trabalhar aqui?");
    await user.type(screen.getByRole("textbox", { name: "Resposta" }), "Porque gosto de back-end.");

    await user.click(screen.getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(api.createProfile).toHaveBeenCalledTimes(1));
    const body = api.createProfile.mock.calls[0]![0];
    expect(body.seniority).toBe("SPECIALIST");
    expect(body.headline).toBe("Back-end Java");
    expect(body.country).toBe("BR");
    expect(body.workAuthorization).toEqual(["BR", "PT"]);
    expect(body.employmentTypes).toEqual(["CLT"]);
    expect(body.workModes).toEqual(["REMOTE", "HYBRID"]);
    expect(body.skills).toEqual([{ name: "Java", level: 3, years: 1, months: 6, days: 10 }]);
    expect(body.languages).toEqual([{ name: "Inglês", level: "B2" }]);
    expect(body.answers).toEqual([{ question: "Por que quer trabalhar aqui?", answer: "Porque gosto de back-end." }]);
    await waitFor(() => expect(api.profile).toHaveBeenCalledWith("novo-id"));
    // Digita muitos campos numa tela com centenas de sugestões de idioma e país: mais folga que o padrão de 30 s.
  }, 90_000);

  it("carrega o perfil salvo com o idioma por extenso e recusa país desconhecido", async () => {
    api.profile.mockResolvedValue({ data: saved });
    const user = userEvent.setup({ delay: null });
    renderAt("/perfis/p1");

    expect(await screen.findByDisplayValue("Inglês")).toBeTruthy();
    expect(screen.getByDisplayValue("Brasil, Portugal")).toBeTruthy();
    expect((screen.getByRole("combobox", { name: "Senioridade" }) as HTMLSelectElement).value).toBe("SPECIALIST");

    const country = screen.getByRole("combobox", { name: "País" });
    await user.clear(country);
    await user.type(country, "Atlântida");
    await user.click(screen.getByRole("button", { name: "Salvar" }));
    expect(await screen.findByText(/País não reconhecido: Atlântida/)).toBeTruthy();
    expect(api.updateProfile).not.toHaveBeenCalled();
  });
});
