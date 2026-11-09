import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AccountSection } from "../pages/AccountSection";
import { I18nProvider } from "../i18n/I18nContext";
import { FrequencyInput, frequencyText } from "./FrequencyInput";
import { PasswordInput } from "./PasswordInput";
import { isNewer, UpdateNotice } from "./UpdateNotice";

const mocks = vi.hoisted(() => ({
  updateName: vi.fn(),
  updateEmail: vi.fn(),
  updatePassword: vi.fn(),
  systemVersion: vi.fn(),
  recoveryStatus: vi.fn(),
  newRecoveryCodes: vi.fn(),
  reloadUser: vi.fn(),
  setAccessToken: vi.fn(),
}));
vi.mock("../api/endpoints", () => ({ api: mocks }));
vi.mock("../api/client", () => ({ setAccessToken: mocks.setAccessToken }));
vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ user: { displayName: "Pessoa", email: "p@teste.dev" }, reloadUser: mocks.reloadUser }),
}));

const ui = (node: React.ReactNode) => render(<I18nProvider>{node}</I18nProvider>);

function Frequency() {
  const [value, setValue] = useState(360);
  return (
    <>
      <FrequencyInput value={value} onChange={setValue} label="F" />
      <output>{value}</output>
    </>
  );
}

beforeEach(() => {
  localStorage.clear();
  localStorage.setItem("jobdragonquest.locale", "pt-BR");
  Object.values(mocks).forEach((mock) => mock.mockReset());
  mocks.recoveryStatus.mockResolvedValue({ data: { remaining: 0 } });
});

describe("issue 172", () => {
  it("compara versões por número e ignora o que não é versão", () => {
    expect(isNewer("0.10.0", "0.9.9")).toBe(true);
    expect(isNewer("0.6.0", "0.6.1")).toBe(false);
    expect(isNewer("0.6.1", "dev")).toBe(false);
  });

  it("mostra e esconde a senha", async () => {
    ui(<PasswordInput aria-label="Campo" defaultValue="segredo" />);
    const input = screen.getByLabelText("Campo");
    expect(input.getAttribute("type")).toBe("password");
    await userEvent.click(screen.getByRole("button", { pressed: false }));
    expect(input.getAttribute("type")).toBe("text");
  });

  it("frequência livre em horas ou dias, sempre em minutos", async () => {
    const user = userEvent.setup();
    ui(<Frequency />);
    const amount = screen.getByRole("spinbutton");
    await user.clear(amount);
    await user.type(amount, "12");
    expect(screen.getByRole("status").textContent).toBe("720");
    await user.selectOptions(screen.getByRole("combobox"), "1440");
    expect(screen.getByRole("status").textContent).toBe(String(12 * 1440));
  });

  it("descreve a frequência", () => {
    const t = ((key: string, values: { count: number }) => `${key}:${values.count}`) as never;
    expect(frequencyText(2880, t)).toBe("frequency.everyDays:2");
    expect(frequencyText(180, t)).toBe("frequency.everyHours:3");
    expect(frequencyText(45, t)).toBe("frequency.everyMinutes:45");
  });

  it("recusa senhas diferentes e troca a senha com o token novo", async () => {
    const user = userEvent.setup();
    mocks.updatePassword.mockResolvedValue({ data: { accessToken: "novo" } });
    const { container } = ui(<AccountSection />);
    const form = container.querySelectorAll("form")[2]!;
    const [current, next, confirm] = Array.from(form.querySelectorAll<HTMLInputElement>("input"));
    await user.type(current!, "senha-atual");
    await user.type(next!, "senha-nova-bem-forte");
    await user.type(confirm!, "outra-senha-diferente");
    await user.click(screen.getByRole("button", { name: "Trocar senha" }));
    expect(screen.getByRole("alert").textContent).toContain("não são iguais");
    expect(mocks.updatePassword).not.toHaveBeenCalled();
    await user.clear(confirm!);
    await user.type(confirm!, "senha-nova-bem-forte");
    await user.click(screen.getByRole("button", { name: "Trocar senha" }));
    await waitFor(() => expect(mocks.setAccessToken).toHaveBeenCalledWith("novo"));
    expect(mocks.updatePassword).toHaveBeenCalledWith("senha-atual", "senha-nova-bem-forte");
  });

  it("avisa de versão nova e deixa dispensar", async () => {
    mocks.systemVersion.mockResolvedValue({ data: { current: __APP_VERSION__, latest: "9.9.9", updateAvailable: true, releaseUrl: "https://github.com/x" } });
    ui(<UpdateNotice />);
    expect(await screen.findByText(/Nova versão disponível: 9.9.9/)).toBeTruthy();
    await userEvent.click(screen.getByRole("button", { name: "Dispensar" }));
    expect(screen.queryByText(/Nova versão disponível/)).toBeNull();
  });
});
