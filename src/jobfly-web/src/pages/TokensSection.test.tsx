import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { TokensSection } from "./TokensSection";

const mocks = vi.hoisted(() => ({ tokens: vi.fn(), createToken: vi.fn(), revokeToken: vi.fn() }));

vi.mock("../api/endpoints", () => ({ api: mocks }));

const active = {
  id: "t1",
  name: "MCP do Claude",
  prefix: "jfp_abc123…",
  createdAt: "2026-11-06T10:00:00Z",
  expiresAt: "2027-02-04T10:00:00Z",
  lastUsedAt: null,
  revokedAt: null,
  active: true,
};

function renderSection() {
  return render(
    <I18nProvider initial="pt-BR">
      <TokensSection />
    </I18nProvider>,
  );
}

describe("tokens de acesso pessoal", () => {
  beforeEach(() => {
    mocks.tokens.mockReset().mockResolvedValue({ data: [active] });
    mocks.createToken.mockReset();
    mocks.revokeToken.mockReset().mockResolvedValue({ data: undefined });
  });

  it("lista sem o valor e mostra o token criado uma única vez", async () => {
    mocks.createToken.mockResolvedValue({ data: { token: { ...active, id: "t2", name: "Congo" }, value: "jfp_" + "x".repeat(43) } });
    renderSection();
    expect(await screen.findByText("MCP do Claude")).toBeTruthy();
    expect(screen.getByText("jfp_abc123…")).toBeTruthy();

    await userEvent.type(screen.getByLabelText("Nome do token"), "Congo", { delay: null });
    await userEvent.click(screen.getByRole("button", { name: "Criar token" }));
    await waitFor(() => expect(mocks.createToken).toHaveBeenCalledWith({ name: "Congo", days: 90 }));
    expect((await screen.findByTestId("token-value")).textContent).toBe("jfp_" + "x".repeat(43));

    await userEvent.click(screen.getAllByRole("button", { name: "×" })[0]!);
    expect(screen.queryByTestId("token-value")).toBeNull();
  });

  it("revoga só depois de confirmar", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    renderSection();
    await userEvent.click(await screen.findByRole("button", { name: "Revogar" }));
    expect(mocks.revokeToken).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "Revogar" }));
    await waitFor(() => expect(mocks.revokeToken).toHaveBeenCalledWith("t1"));
    confirm.mockRestore();
  });
});
