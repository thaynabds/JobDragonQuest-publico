import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { A11yAccountSync } from "./A11yAccountSync";
import { A11yProvider, useA11y } from "./A11yContext";
import { DEFAULT_PREFS } from "./prefs";

const api = vi.hoisted(() => ({ accessibility: vi.fn(), saveAccessibility: vi.fn() }));
vi.mock("../api/endpoints", () => ({ api }));
const auth = vi.hoisted(() => ({ user: { id: "u1" } as { id: string } | null }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => auth }));

function Probe() {
  const { prefs, update } = useA11y();
  return (
    <div>
      <span data-testid="theme">{prefs.theme}</span>
      <span data-testid="scale">{prefs.fontScale}</span>
      <button type="button" onClick={() => update({ dyslexiaFont: true })}>
        mudar
      </button>
    </div>
  );
}

beforeEach(() => {
  Object.values(api).forEach((mock) => mock.mockReset());
  api.saveAccessibility.mockResolvedValue({ data: {} });
  auth.user = { id: "u1" };
});

describe("acessibilidade na conta (#126)", () => {
  it("no login aplica as preferências da conta, ignorando valores estranhos, e salva as mudanças", async () => {
    api.accessibility.mockResolvedValue({ data: { theme: "kuro", fontScale: 130, colorFilter: "url(javascript:x)" } });
    const user = userEvent.setup({ delay: null });
    render(
      <A11yProvider initial={DEFAULT_PREFS}>
        <A11yAccountSync />
        <Probe />
      </A11yProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("theme").textContent).toBe("kuro"));
    expect(screen.getByTestId("scale").textContent).toBe("130");
    // Aplicar o que veio da conta não gera outro envio.
    expect(api.saveAccessibility).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "mudar" }));
    await waitFor(() => expect(api.saveAccessibility).toHaveBeenCalledTimes(1), { timeout: 3000 });
    expect(api.saveAccessibility.mock.calls[0]![0]).toMatchObject({ theme: "kuro", dyslexiaFont: true, colorFilter: "none" });
  });

  it("conta sem preferências recebe as deste navegador; sem login nada é enviado", async () => {
    api.accessibility.mockResolvedValue({ data: null });
    render(
      <A11yProvider initial={{ ...DEFAULT_PREFS, theme: "dark" }}>
        <A11yAccountSync />
        <Probe />
      </A11yProvider>,
    );
    await waitFor(() => expect(api.saveAccessibility).toHaveBeenCalledWith(expect.objectContaining({ theme: "dark" })));

    auth.user = null;
    api.accessibility.mockClear();
    render(
      <A11yProvider initial={DEFAULT_PREFS}>
        <A11yAccountSync />
      </A11yProvider>,
    );
    expect(api.accessibility).not.toHaveBeenCalled();
  });
});
