import { readFileSync } from "node:fs";

import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { LOCALES } from "../i18n/messages";
import { QUICK_ACTIONS, QuickActions } from "./QuickActions";

// Rotas declaradas no App, lidas do código: um atalho para tela que não existe quebraria o teste.
const routes = new Set(
  [...readFileSync(`${process.cwd()}/src/App.tsx`, "utf8").matchAll(/<Route (?:index|path="([^"]+)")/g)].map((m) => `/${m[1] ?? ""}`),
);

describe("o que dá para fazer no painel (#223)", () => {
  it("mostra cada ação com link para a tela certa", () => {
    render(
      <I18nProvider initial="pt-BR">
        <MemoryRouter>
          <QuickActions />
        </MemoryRouter>
      </I18nProvider>,
    );
    const links = screen.getAllByRole("link");
    expect(links).toHaveLength(QUICK_ACTIONS.length);
    expect(screen.getByRole("link", { name: /Conversar com a IA/ }).getAttribute("href")).toBe("/assistentes");
    expect(screen.getByRole("link", { name: /Preparar candidatura/ }).getAttribute("href")).toBe("/vagas");
  });

  it("todo atalho aponta para uma rota que existe", () => {
    expect(routes.size).toBeGreaterThan(5);
    expect(routes.has("/nao-existe")).toBe(false);
    for (const action of QUICK_ACTIONS) expect(routes.has(action.to), action.to).toBe(true);
  });

  it("tem texto em todos os idiomas completos", () => {
    for (const locale of ["pt-BR", "en", "es"] as const) {
      const messages = LOCALES[locale] as Record<string, string>;
      for (const action of QUICK_ACTIONS) {
        expect(messages[`quick.${action.id}.title`], `${locale} ${action.id}`).toBeTruthy();
        expect(messages[`quick.${action.id}.text`], `${locale} ${action.id}`).toBeTruthy();
      }
    }
  });
});
