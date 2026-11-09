// Textos da interface, um arquivo por idioma em ./locales. O português é a referência: os outros são tipados contra
// ele, então o TypeScript recusa chave faltando ou sobrando. Para criar um idioma novo automaticamente, veja
// scripts/novo-idioma.mjs; depois registre o arquivo em LOCALES e LOCALE_NAMES.
import { en } from "./locales/en";
import { es } from "./locales/es";
import { ar, bn, fr, hi, id, it, ja, ru, zh } from "./locales/parciais";
import { ptBR } from "./locales/pt-BR";

export type { MessageKey, Messages } from "./locales/pt-BR";

export const LOCALES = {
  "pt-BR": ptBR,
  en,
  es,
  fr: { ...en, ...fr },
  it: { ...en, ...it },
  zh: { ...en, ...zh },
  ja: { ...en, ...ja },
  hi: { ...en, ...hi },
  ar: { ...en, ...ar },
  bn: { ...en, ...bn },
  ru: { ...en, ...ru },
  id: { ...en, ...id },
} as const;
export type Locale = keyof typeof LOCALES;

/** Nome de cada idioma escrito nele mesmo, para o seletor. */
export const LOCALE_NAMES: Record<Locale, string> = { "pt-BR": "Português", en: "English", es: "Español", fr: "Français", it: "Italiano", zh: "中文", ja: "日本語", hi: "हिन्दी", ar: "العربية", bn: "বাংলা", ru: "Русский", id: "Bahasa Indonesia" };

export function isLocale(value: unknown): value is Locale {
  return typeof value === "string" && Object.prototype.hasOwnProperty.call(LOCALES, value);
}

/**
 * Escolhe o idioma pela lista de preferências do navegador (navigator.languages), na ordem: primeiro a combinação
 * exata (pt-BR), depois só a língua (pt-PT vira pt-BR, es-AR vira es). Sem nada conhecido, inglês.
 */
export function detectLocale(preferred: readonly string[] | undefined): Locale {
  const available = Object.keys(LOCALES) as Locale[];
  for (const tag of preferred ?? []) {
    const exact = available.find((locale) => locale.toLowerCase() === tag.toLowerCase());
    if (exact) {
      return exact;
    }
    const language = tag.toLowerCase().split("-")[0];
    const sameLanguage = available.find((locale) => locale.toLowerCase().split("-")[0] === language);
    if (sameLanguage) {
      return sameLanguage;
    }
  }
  return "en";
}

/** Substitui {nome} pelos valores. Os valores são texto: o React escapa na hora de exibir. */
export function format(template: string, values: Record<string, string | number> = {}): string {
  return template.replace(/\{(\w+)\}/g, (match, name: string) => (name in values ? String(values[name]) : match));
}

/** Marcadores {x} de um texto, para conferir que a tradução manteve os mesmos. */
export function placeholders(template: string): string[] {
  return [...template.matchAll(/\{(\w+)\}/g)].map((match) => match[1] ?? "").sort();
}
