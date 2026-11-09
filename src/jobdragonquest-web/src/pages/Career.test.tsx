import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { CareerPage } from "./CareerPage";
import { ResumePage } from "./ResumePage";

const api = vi.hoisted(() => ({ currencyRates: vi.fn(() => Promise.resolve({ data: { base: "USD", updatedAt: "hoje", rates: { USD: 1, BRL: 5 } } })), profile: vi.fn(() => Promise.resolve({ data: { options: null } })), resume: vi.fn(), coverLetter: vi.fn(), profiles: vi.fn(), careerSkills: vi.fn(), careerSalary: vi.fn() }));
vi.mock("../api/endpoints", () => ({ api }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => ({ user: { displayName: "Pessoa Teste", email: "pessoa@teste.dev" } }) }));

beforeEach(() => Object.values(api).forEach((mock) => mock.mockReset()));

const entry = (title: string, highlighted: boolean) => ({
  kind: "EXPERIENCE",
  title,
  organization: "Empresa A",
  startDate: "2025-01-01",
  endDate: null,
  current: true,
  location: null,
  description: "APIs",
  skills: ["Spring Boot"],
  highlighted,
});

describe("currículo e carreira (#118)", () => {
  it("mostra o currículo ajustado à vaga, a carta e imprime pelo navegador", async () => {
    api.resume.mockResolvedValue({
      data: {
        headline: "Back-end Java",
        currentTitle: null,
        location: "Recife, BR",
        summary: "Gosto de APIs.",
        links: ["https://linkedin.com/in/teste"],
        skills: [
          { name: "Spring Boot", level: 3, time: "8 meses", highlighted: true },
          { name: "Python", level: 2, time: null, highlighted: false },
        ],
        experience: [entry("Estágio", true)],
        education: [],
        other: [],
        languages: ["en (B2)"],
        jobTitle: "Desenvolvedora Java",
        company: "Empresa X",
      },
    });
    api.coverLetter.mockResolvedValue({ data: { text: "Olá, equipe da Empresa X,", missing: ["resumo profissional"] } });
    const print = vi.spyOn(window, "print").mockImplementation(() => undefined);
    const user = userEvent.setup({ delay: null });
    render(
      <I18nProvider initial="pt-BR">
        <MemoryRouter initialEntries={["/perfis/p1/curriculo?vaga=j1"]}>
          <Routes>
            <Route path="/perfis/:id/curriculo" element={<ResumePage />} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>,
    );
    expect(await screen.findByRole("heading", { name: "Pessoa Teste" })).toBeTruthy();
    expect(api.resume).toHaveBeenCalledWith("p1", "j1");
    expect(screen.getByText(/Ajustado para Desenvolvedora Java na Empresa X/)).toBeTruthy();
    expect(screen.getByText("pedida na vaga")).toBeTruthy();
    expect(await screen.findByText("Olá, equipe da Empresa X,")).toBeTruthy();
    expect(screen.getByText(/complete no perfil: resumo profissional/)).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Imprimir ou salvar em PDF" }));
    expect(print).toHaveBeenCalled();
  });

  it("mostra competências a desenvolver e a faixa salarial das vagas da conta", async () => {
    api.profiles.mockResolvedValue({ data: [{ id: "p1", name: "Java", seniority: "JUNIOR", desiredTitles: [], skillCount: 2 }] });
    api.careerSkills.mockResolvedValue({ data: [{ skill: "kotlin", jobs: 2, percent: 100, inProfile: false }] });
    api.careerSalary.mockResolvedValue({ data: [{ jobs: 2, currency: "BRL", period: "MONTH", min: 5000, median: 6500, max: 8000 }] });
    const user = userEvent.setup({ delay: null });
    render(
      <I18nProvider initial="pt-BR">
        <MemoryRouter>
          <CareerPage />
        </MemoryRouter>
      </I18nProvider>,
    );
    await screen.findByRole("option", { name: "Java" });
    await user.selectOptions(screen.getByRole("combobox", { name: "Perfil" }), "p1");
    await user.type(screen.getByRole("textbox", { name: "Cargo (opcional)" }), "java");
    await user.click(screen.getByRole("button", { name: "Filtrar" }));
    await waitFor(() => expect(api.careerSkills).toHaveBeenLastCalledWith({ profileId: "p1", title: "java" }));
    expect(await screen.findByText("para desenvolver")).toBeTruthy();
    expect(screen.getByText(/mín. 5000 · mediana 6500 · máx. 8000/)).toBeTruthy();
    expect(screen.getByText("Roteiro para negociar")).toBeTruthy();
  });
});
