import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { EnvioAutomaticoSection } from "./EnvioAutomaticoSection";

const mocks = vi.hoisted(() => ({
  envioAutomatico: vi.fn(),
  definirEnvioAutomatico: vi.fn(),
}));
vi.mock("../api/endpoints", () => ({ api: mocks }));

const renderSection = () =>
  render(
    <I18nProvider initial="pt-BR">
      <EnvioAutomaticoSection />
    </I18nProvider>,
  );

describe("envio automático nas configurações", () => {
  beforeEach(() => {
    mocks.envioAutomatico.mockReset();
    mocks.definirEnvioAutomatico.mockReset();
  });

  it("não oferece ligar antes do aprendizado completo", async () => {
    mocks.envioAutomatico.mockResolvedValue({ data: { confirmados: 3, limite: 10, liberado: false, ativo: false } });
    renderSection();
    expect(await screen.findByText(/3 de 10/)).toBeTruthy();
    expect(screen.queryByRole("button", { name: /Ligar envio automático/ })).toBeNull();
  });

  it("liga só depois de aceitar o aviso", async () => {
    mocks.envioAutomatico.mockResolvedValue({ data: { confirmados: 10, limite: 10, liberado: true, ativo: false } });
    mocks.definirEnvioAutomatico.mockResolvedValue({ data: { confirmados: 10, limite: 10, liberado: true, ativo: true } });
    renderSection();
    const ligar = await screen.findByRole("button", { name: "Ligar envio automático" });
    expect((ligar as HTMLButtonElement).disabled).toBe(true);
    await userEvent.click(screen.getByRole("checkbox"));
    expect((ligar as HTMLButtonElement).disabled).toBe(false);
    await userEvent.click(ligar);
    await waitFor(() => expect(mocks.definirEnvioAutomatico).toHaveBeenCalledWith({ ativo: true, avisoAceito: true }));
    expect(await screen.findByRole("button", { name: "Desligar envio automático" })).toBeTruthy();
  });

  it("desliga sem exigir aviso", async () => {
    mocks.envioAutomatico.mockResolvedValue({ data: { confirmados: 12, limite: 10, liberado: true, ativo: true } });
    mocks.definirEnvioAutomatico.mockResolvedValue({ data: { confirmados: 12, limite: 10, liberado: true, ativo: false } });
    renderSection();
    await userEvent.click(await screen.findByRole("button", { name: "Desligar envio automático" }));
    await waitFor(() => expect(mocks.definirEnvioAutomatico).toHaveBeenCalledWith({ ativo: false, avisoAceito: false }));
  });
});
