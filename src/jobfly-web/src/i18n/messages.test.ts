import { describe, expect, it } from "vitest";

import { APPLICATION_STATES, ORIGINS } from "../api/types";
import { format, LOCALES } from "./messages";

describe("traduções", () => {
  it("português e inglês têm as mesmas chaves, todas preenchidas", () => {
    const pt = Object.keys(LOCALES["pt-BR"]).sort();
    const en = Object.keys(LOCALES.en).sort();
    expect(en).toEqual(pt);
    for (const messages of Object.values(LOCALES)) {
      for (const [key, value] of Object.entries(messages)) {
        expect(value.trim(), key).not.toBe("");
      }
    }
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

  it("substitui variáveis e mantém as desconhecidas", () => {
    expect(format("{created} criada(s), {x}", { created: 3 })).toBe("3 criada(s), {x}");
  });
});
