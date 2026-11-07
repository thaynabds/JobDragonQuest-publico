import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { AssistCard } from "./AssistCard";

const api = vi.hoisted(() => ({ assistAnswers: vi.fn(), assistReview: vi.fn() }));
vi.mock("../api/endpoints", () => ({ api }));

beforeEach(() => Object.values(api).forEach((mock) => mock.mockReset()));

const renderCard = () =>
  render(
    <I18nProvider initial="pt-BR">
      <AssistCard
        jobId="j1"
        profiles={[
          { id: "p1", name: "Java" },
          { id: "p2", name: "Dados" },
        ]}
      />
    </I18nProvider>,
  );

describe("preparar a candidatura (#115)", () => {
  it("mostra respostas do perfil e marca o que falta sem inventar", async () => {
    api.assistAnswers.mockResolvedValue({
      data: [
        { category: "salary", question: "Qual a sua pretensão salarial?", answer: "5000 BRL", source: "salaryExpectation", missing: null },
        {
          category: "experience",
          question: "Quanto tempo de experiência você tem com Kotlin?",
          answer: null,
          source: "skills",
          missing: "O perfil não declara Kotlin. Só adicione se você realmente tiver essa experiência.",
        },
      ],
    });
    const user = userEvent.setup({ delay: null });
    renderCard();
    await user.selectOptions(screen.getByRole("combobox", { name: "Perfil" }), "p2");
    await user.click(screen.getByRole("button", { name: "Analisar perguntas e respostas para esta vaga" }));
    await waitFor(() => expect(api.assistAnswers).toHaveBeenCalledWith("j1", "p2"));
    expect(await screen.findByText("5000 BRL")).toBeTruthy();
    expect(screen.getByText(/Só adicione se você realmente/)).toBeTruthy();
    expect(screen.getAllByRole("button", { name: "Copiar resposta" })).toHaveLength(1);
  });

  it("mostra a revisão com impedimentos", async () => {
    api.assistReview.mockResolvedValue({
      data: { ready: false, checks: [{ code: "blocker_work_mode", status: "BLOCK", message: "A vaga é REMOTE e o perfil aceita [ONSITE]" }] },
    });
    const user = userEvent.setup({ delay: null });
    renderCard();
    await user.click(screen.getByRole("button", { name: "Revisar candidatura antes de enviar" }));
    expect(await screen.findByText("Há impedimentos: resolva antes de enviar.")).toBeTruthy();
    expect(screen.getByText("Impede")).toBeTruthy();
    expect(screen.getByText(/A vaga é REMOTE/)).toBeTruthy();
  });
});
