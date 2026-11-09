import { describe, expect, it } from "vitest";

import { APPLICATION_STATES, ORIGINS } from "../api/types";
import { detectLocale, format, LOCALE_NAMES, LOCALES, placeholders } from "./messages";

describe("traduções", () => {
  const reference = LOCALES["pt-BR"];

  it("todos os idiomas têm as mesmas chaves do português, todas preenchidas", () => {
    const keys = Object.keys(reference).sort();
    for (const [code, messages] of Object.entries(LOCALES)) {
      expect(Object.keys(messages).sort(), code).toEqual(keys);
      for (const [key, value] of Object.entries(messages)) {
        expect(value.trim(), `${code} ${key}`).not.toBe("");
      }
    }
  });

  it("cada tradução mantém exatamente os marcadores {x} do português", () => {
    for (const [code, messages] of Object.entries(LOCALES)) {
      for (const [key, value] of Object.entries(reference)) {
        expect(placeholders(messages[key as keyof typeof reference]), `${code} ${key}`).toEqual(placeholders(value));
      }
    }
  });

  it("todo idioma registrado tem nome no seletor", () => {
    expect(Object.keys(LOCALE_NAMES).sort()).toEqual(Object.keys(LOCALES).sort());
    expect(Object.keys(LOCALES)).toEqual(expect.arrayContaining(["pt-BR", "en", "es"]));
  });

  it("todas as etapas e origens têm rótulo", () => {
    for (const messages of Object.values(LOCALES)) {
      for (const state of APPLICATION_STATES) {
        expect(messages).toHaveProperty(`state.${state}`);
      }
      for (const origin of ORIGINS) {
        expect(messages).toHaveProperty(`origin.${origin}`);
      }
    }
  });

  it("detecta o idioma pela ordem do navegador", () => {
    expect(detectLocale(["es-AR", "en-US"])).toBe("es");
    expect(detectLocale(["pt-PT"])).toBe("pt-BR");
    expect(detectLocale(["pt-br"])).toBe("pt-BR");
    expect(detectLocale(["de-DE", "en-GB"])).toBe("en");
    expect(detectLocale(["ko"])).toBe("en");
    expect(detectLocale(undefined)).toBe("en");
  });

  it("substitui variáveis e mantém as desconhecidas", () => {
    expect(format("{created} criada(s), {x}", { created: 3 })).toBe("3 criada(s), {x}");
    expect(placeholders("{b} e {a}")).toEqual(["a", "b"]);
  });
});
