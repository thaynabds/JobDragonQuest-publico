import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { A11yProvider, applyToDocument } from "./A11yContext";
import { A11yPanel } from "./A11yPanel";
import { COLOR_MATRICES, feValues } from "./filters";
import { COLOR_FILTERS, DEFAULT_PREFS, documentState, sanitize, STORAGE_KEY, THEMES, TOGGLES } from "./prefs";

const root = document.documentElement;

function renderPanel() {
  return render(
    <I18nProvider initial="pt-BR">
      <A11yProvider>
        <main>
          <p>Conteúdo da página para ler.</p>
          <video data-testid="video" />
        </main>
        <A11yPanel />
      </A11yProvider>
    </I18nProvider>,
  );
}

function resetDocument() {
  root.className = "";
  root.removeAttribute("data-theme");
  root.removeAttribute("data-font-scale");
  window.localStorage.clear();
}

describe("preferências de acessibilidade", () => {
  beforeEach(resetDocument);

  it("traz os 7 temas e os 6 filtros do Congo e os recursos do a11y-widget", () => {
    expect(THEMES.filter((theme) => theme !== "system")).toEqual(["light", "dark", "high_contrast", "kuro", "pink_void", "red_void", "yellow_void"]);
    expect(COLOR_FILTERS.filter((filter) => filter !== "none")).toEqual(["invert", "smart_invert", "protanopia", "deuteranopia", "tritanopia", "grayscale"]);
    // 13 recursos liga/desliga + tamanho do texto + modo escuro (tema) + inverter + daltonismo (filtros) + leitura em voz alta + persistência = 19.
    expect(TOGGLES).toHaveLength(13);
  });

  it("descarta valores adulterados no localStorage", () => {
    const prefs = sanitize({ theme: "<script>", colorFilter: "url(evil)", fontScale: 999, contrast: "true", bigCursor: true, extra: 1 });
    expect(prefs.theme).toBe("system");
    expect(prefs.colorFilter).toBe("none");
    expect(prefs.fontScale).toBe(100);
    expect(prefs.contrast).toBe(false);
    expect(prefs.bigCursor).toBe(true);
    expect(prefs).not.toHaveProperty("extra");
    expect(sanitize(null)).toEqual(DEFAULT_PREFS);
  });

  it("aplica tema, escala e classes de lista fixa e limpa as antigas", () => {
    applyToDocument({ ...DEFAULT_PREFS, theme: "kuro", fontScale: 130, colorFilter: "protanopia", hideImages: true });
    expect(root.getAttribute("data-theme")).toBe("kuro");
    expect(root.getAttribute("data-font-scale")).toBe("130");
    expect(root.classList.contains("a11y-filter-protanopia")).toBe(true);
    expect(root.classList.contains("a11y-hide-images")).toBe(true);
    expect(root.classList.contains("a11y-filtered")).toBe(true);
    applyToDocument(DEFAULT_PREFS);
    expect(root.hasAttribute("data-theme")).toBe(false);
    expect(root.className).toBe("");
    expect(documentState({ ...DEFAULT_PREFS, saturate: true }).classes).toEqual(["a11y-saturate", "a11y-filtered"]);
  });

  it("usa as matrizes do Daltonizer do Android, como no Congo", () => {
    expect(COLOR_MATRICES.protanopia[0]).toEqual([0.46111, 0.53889, 0.0]);
    expect(COLOR_MATRICES.deuteranopia[2]).toEqual([-0.36845, 0.36845, 1.0]);
    expect(COLOR_MATRICES.tritanopia[0]).toEqual([1.0, -1.15102, 1.15102]);
    expect(COLOR_MATRICES.grayscale[1]).toEqual([0.2126, 0.7152, 0.0722]);
    expect(feValues(COLOR_MATRICES.grayscale)).toBe("0.2126 0.7152 0.0722 0 0 0.2126 0.7152 0.0722 0 0 0.2126 0.7152 0.0722 0 0 0 0 0 1 0");
  });
});

describe("painel de acessibilidade", () => {
  const spoken: { text: string; lang: string }[] = [];

  beforeEach(() => {
    resetDocument();
    spoken.length = 0;
    class FakeUtterance {
      lang = "";
      onend: (() => void) | null = null;
      onerror: (() => void) | null = null;
      constructor(public text: string) {}
    }
    vi.stubGlobal("SpeechSynthesisUtterance", FakeUtterance);
    vi.stubGlobal("speechSynthesis", {
      cancel: vi.fn(),
      speak: (utterance: { text: string; lang: string }) => spoken.push({ text: utterance.text, lang: utterance.lang }),
    });
    window.HTMLMediaElement.prototype.pause = vi.fn();
  });

  afterEach(() => vi.unstubAllGlobals());

  it("abre com Alt+A, fecha com Esc e tem os filtros SVG na página", async () => {
    const { container } = renderPanel();
    expect(container.querySelectorAll("filter")).toHaveLength(4);
    expect(container.querySelector("#a11y-protanopia feColorMatrix")).not.toBeNull();
    fireEvent.keyDown(window, { key: "a", altKey: true });
    expect(await screen.findByRole("dialog", { name: "Acessibilidade" })).toBeTruthy();
    fireEvent.keyDown(window, { key: "Escape" });
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("aplica tema, filtro, tamanho e todos os recursos, salva e restaura", async () => {
    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "Acessibilidade" }));
    const dialog = screen.getByRole("dialog");
    await userEvent.selectOptions(within(dialog).getByLabelText("Tema"), "yellow_void");
    expect(root.getAttribute("data-theme")).toBe("yellow_void");
    await userEvent.selectOptions(within(dialog).getByLabelText("Filtro de cor"), "smart_invert");
    expect(root.classList.contains("a11y-filter-smart_invert")).toBe(true);
    await userEvent.click(within(dialog).getByRole("button", { name: "Aumentar o texto" }));
    await userEvent.click(within(dialog).getByRole("button", { name: "Aumentar o texto" }));
    expect(root.getAttribute("data-font-scale")).toBe("120");

    for (const checkbox of within(dialog).getAllByRole("checkbox")) {
      await userEvent.click(checkbox);
    }
    for (const cls of ["a11y-contrast", "a11y-saturate", "a11y-hide-images", "a11y-dyslexia-font", "a11y-line-height", "a11y-spacing",
      "a11y-highlight-links", "a11y-highlight-headings", "a11y-pause-animations", "a11y-pause-videos", "a11y-big-cursor",
      "a11y-focus-ring", "a11y-reading-guide"]) {
      expect(root.classList.contains(cls), cls).toBe(true);
    }
    expect(document.querySelector(".a11y-reading-guide-band")).not.toBeNull();
    expect(window.HTMLMediaElement.prototype.pause).toHaveBeenCalled();
    const saved = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "{}");
    expect(saved).toMatchObject({ theme: "yellow_void", colorFilter: "smart_invert", fontScale: 120, readingGuide: true });

    await userEvent.click(within(dialog).getByRole("button", { name: "Restaurar padrões" }));
    expect(root.hasAttribute("data-theme")).toBe(false);
    expect(root.className).toBe("");
    expect(root.getAttribute("data-font-scale")).toBe("100");
  });

  it("lê em voz alta a seleção ou a página no idioma do painel", async () => {
    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "Acessibilidade" }));
    await userEvent.click(screen.getByRole("button", { name: "Ler em voz alta" }));
    expect(spoken).toHaveLength(1);
    expect(spoken[0]!.lang).toBe("pt-BR");
    expect(spoken[0]!.text).toContain("Conteúdo da página para ler.");
    expect(screen.getByRole("button", { name: "Parar a leitura" })).toBeTruthy();
  });

  it("recupera as preferências salvas ao abrir de novo", () => {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ theme: "high_contrast", fontScale: 140, focusRing: true }));
    renderPanel();
    expect(root.getAttribute("data-theme")).toBe("high_contrast");
    expect(root.getAttribute("data-font-scale")).toBe("140");
    expect(root.classList.contains("a11y-focus-ring")).toBe(true);
  });
});
