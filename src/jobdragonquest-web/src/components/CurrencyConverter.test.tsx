import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { convert, CurrencyConverter } from "./CurrencyConverter";
import { Field } from "./ui";

const mocks = vi.hoisted(() => ({ currencyRates: vi.fn() }));
vi.mock("../api/endpoints", () => ({ api: mocks }));

describe("issue 174", () => {
  it("converte pela base em dólar", () => {
    const rates = { USD: 1, BRL: 5, CAD: 1.25 };
    expect(convert(100, "USD", "BRL", rates)).toBe(500);
    expect(convert(125, "CAD", "BRL", rates)).toBe(500);
    expect(convert(1, "XXX", "BRL", rates)).toBeNull();
  });

  it("mostra o valor convertido e cita a fonte", async () => {
    mocks.currencyRates.mockResolvedValue({ data: { base: "USD", updatedAt: "hoje", rates: { USD: 1, BRL: 5, CAD: 1.25 } } });
    render(
      <I18nProvider initial="pt-BR">
        <CurrencyConverter initialAmount={2000} initialCurrency="CAD" />
      </I18nProvider>,
    );
    expect(await screen.findByText(/8\.000,00/)).toBeTruthy();
    expect(screen.getByRole("link", { name: "Rates By Exchange Rate API" })).toBeTruthy();
  });

  it("o i de ajuda não muda o nome do campo e mostra a explicação", async () => {
    render(
      <Field label="Campo" info="Explicação do campo">
        <input />
      </Field>,
    );
    expect(screen.getByRole("textbox", { name: "Campo" })).toBeTruthy();
    const button = screen.getByRole("button", { name: "Explicação do campo" });
    await userEvent.hover(button);
    expect(screen.getByRole("tooltip").textContent).toBe("Explicação do campo");
  });
});
