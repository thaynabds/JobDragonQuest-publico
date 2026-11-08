// Testes ponta a ponta no navegador (Playwright). Rodam contra o painel e a API de verdade, com um banco limpo:
// no CI pelo job "e2e"; localmente só contra uma instalação de teste, nunca contra o seu banco com dados reais.
// Uso: E2E_BASE_URL=http://127.0.0.1:5173 npm run e2e
import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "e2e",
  timeout: 60_000,
  retries: 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never", outputFolder: "playwright-report" }]] : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://127.0.0.1:5173",
    locale: "pt-BR",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
