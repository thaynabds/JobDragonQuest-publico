// Preferências de acessibilidade. Ficam só neste navegador (localStorage): não são dado sensível e funcionam antes
// do login. Temas e filtros vêm do Congo Grey Parrot; os demais recursos, do a11y-widget.

export const THEMES = ["system", "light", "dark", "high_contrast", "kuro", "pink_void", "red_void", "yellow_void"] as const;
export type Theme = (typeof THEMES)[number];

export const COLOR_FILTERS = ["none", "invert", "smart_invert", "protanopia", "deuteranopia", "tritanopia", "grayscale"] as const;
export type ColorFilter = (typeof COLOR_FILTERS)[number];

export const FONT_SCALES = [80, 90, 100, 110, 120, 130, 140] as const;
export type FontScale = (typeof FONT_SCALES)[number];

/** Recursos liga/desliga do a11y-widget. */
export const TOGGLES = [
  "contrast",
  "saturate",
  "hideImages",
  "dyslexiaFont",
  "lineHeight",
  "spacing",
  "highlightLinks",
  "highlightHeadings",
  "pauseAnimations",
  "pauseVideos",
  "bigCursor",
  "focusRing",
  "readingGuide",
] as const;
export type Toggle = (typeof TOGGLES)[number];

export interface A11yPrefs extends Record<Toggle, boolean> {
  theme: Theme;
  colorFilter: ColorFilter;
  fontScale: FontScale;
}

export const DEFAULT_PREFS: A11yPrefs = {
  theme: "system",
  colorFilter: "none",
  fontScale: 100,
  contrast: false,
  saturate: false,
  hideImages: false,
  dyslexiaFont: false,
  lineHeight: false,
  spacing: false,
  highlightLinks: false,
  highlightHeadings: false,
  pauseAnimations: false,
  pauseVideos: false,
  bigCursor: false,
  focusRing: false,
  readingGuide: false,
};

export const STORAGE_KEY = "jobfly.a11y";

function oneOf<T extends string | number>(list: readonly T[], value: unknown, fallback: T): T {
  return list.includes(value as T) ? (value as T) : fallback;
}

/** Aceita só valores conhecidos: um localStorage adulterado não injeta classe nem valor estranho na página. */
export function sanitize(raw: unknown): A11yPrefs {
  const source = raw && typeof raw === "object" ? (raw as Record<string, unknown>) : {};
  const prefs: A11yPrefs = {
    ...DEFAULT_PREFS,
    theme: oneOf(THEMES, source.theme, DEFAULT_PREFS.theme),
    colorFilter: oneOf(COLOR_FILTERS, source.colorFilter, DEFAULT_PREFS.colorFilter),
    fontScale: oneOf(FONT_SCALES, source.fontScale, DEFAULT_PREFS.fontScale),
  };
  for (const toggle of TOGGLES) {
    prefs[toggle] = source[toggle] === true;
  }
  return prefs;
}

export function loadPrefs(): A11yPrefs {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    return saved ? sanitize(JSON.parse(saved)) : DEFAULT_PREFS;
  } catch {
    return DEFAULT_PREFS;
  }
}

export function savePrefs(prefs: A11yPrefs): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs));
  } catch {
    // Armazenamento bloqueado: as preferências valem só nesta visita.
  }
}

/** Classes e atributos aplicados no <html>. Tudo vem de listas fixas, nunca de texto livre. */
export function documentState(prefs: A11yPrefs): { theme: Theme; fontScale: FontScale; classes: string[] } {
  const classes: string[] = [];
  if (prefs.colorFilter !== "none") {
    classes.push(`a11y-filter-${prefs.colorFilter}`);
  }
  for (const toggle of TOGGLES) {
    if (prefs[toggle]) {
      classes.push(`a11y-${toggle.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`);
    }
  }
  if (prefs.colorFilter !== "none" || prefs.contrast || prefs.saturate) {
    classes.push("a11y-filtered");
  }
  return { theme: prefs.theme, fontScale: prefs.fontScale, classes };
}

/** Todas as classes que este módulo pode pôr no <html>, para limpar antes de aplicar de novo. */
export const ALL_CLASSES: string[] = [
  ...COLOR_FILTERS.filter((f) => f !== "none").map((f) => `a11y-filter-${f}`),
  ...TOGGLES.map((t) => `a11y-${t.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`),
  "a11y-filtered",
];
