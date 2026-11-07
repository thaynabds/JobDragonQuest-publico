import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

import { ApiError } from "../api/client";
import { detectLocale, format, isLocale, LOCALES, type Locale, type MessageKey } from "./messages";

interface I18n {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: MessageKey, values?: Record<string, string | number>) => string;
  /** Rótulo de um enum ("state.SUBMITTED"); cai no valor bruto se a chave não existir. */
  label: (prefix: string, value: string | null | undefined) => string;
  /** Mensagem amigável para um erro da API, pelo código estável. */
  errorMessage: (error: unknown) => string;
  formatDate: (iso: string | null | undefined, withTime?: boolean) => string;
}

const STORAGE_KEY = "jobdragonquest.locale";
const I18nContext = createContext<I18n | null>(null);

function initialLocale(): Locale {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    if (isLocale(saved)) {
      return saved;
    }
  } catch {
    // Armazenamento bloqueado: usa o idioma do navegador.
  }
  // Primeiro acesso: segue a ordem de idiomas do navegador (navigator.languages).
  return detectLocale(navigator.languages?.length ? navigator.languages : [navigator.language]);
}

export function I18nProvider({ children, initial }: { children: ReactNode; initial?: Locale }) {
  const [locale, setLocaleState] = useState<Locale>(initial ?? initialLocale);

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next);
    try {
      window.localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Preferência só não fica salva.
    }
  }, []);

  const value = useMemo<I18n>(() => {
    const messages = LOCALES[locale];
    const t = (key: MessageKey, values?: Record<string, string | number>) => format(messages[key] ?? key, values);
    const label = (prefix: string, raw: string | null | undefined) => {
      if (!raw) {
        return messages["common.none"];
      }
      const key = `${prefix}.${raw}` as MessageKey;
      return key in messages ? messages[key] : raw;
    };
    const errorMessage = (error: unknown) => {
      if (error instanceof ApiError) {
        const key = `error.${error.code}` as MessageKey;
        return key in messages ? messages[key] : error.message || messages["error.generic"];
      }
      if (error instanceof TypeError) {
        return messages["error.network"];
      }
      return messages["error.generic"];
    };
    const formatter = new Intl.DateTimeFormat(locale, { dateStyle: "medium" });
    const formatterTime = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" });
    const formatDate = (iso: string | null | undefined, withTime = false) => {
      if (!iso) {
        return messages["common.none"];
      }
      const date = new Date(iso);
      return Number.isNaN(date.getTime()) ? messages["common.none"] : (withTime ? formatterTime : formatter).format(date);
    };
    return { locale, setLocale, t, label, errorMessage, formatDate };
  }, [locale, setLocale]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18n {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error("useI18n fora do I18nProvider");
  }
  return context;
}
