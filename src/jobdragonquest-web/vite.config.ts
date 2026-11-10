/// <reference types="vitest/config" />
import { fileURLToPath } from "node:url";

import react from "@vitejs/plugin-react";
import { defineConfig, type Plugin } from "vite";

import { AI_HOSTS } from "./src/assistant/hosts";

// CSP só no build de produção: o modo de desenvolvimento do Vite injeta um script inline (React Refresh).
const CSP =
  "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' https://cdn.jsdelivr.net; connect-src 'self' " + AI_HOSTS + "; " +
  "base-uri 'self'; form-action 'self'; object-src 'none'";
// frame-ancestors não vale em <meta>: o navegador ignora. A proteção contra frames fica para o proxy de produção.
const contentSecurityPolicy: Plugin = {
  name: "jobdragonquest-csp",
  apply: "build",
  transformIndexHtml: (html) =>
    html.replace("<head>", `<head>\n    <meta http-equiv="Content-Security-Policy" content="${CSP}" />`),
};

// O painel fala com a API pelo mesmo endereço (proxy /api), então o cookie de sessão fica restrito a esta origem.
// Servidor e preview escutam só em 127.0.0.1: outros aparelhos da rede não alcançam o painel.
const api = process.env.JOBDRAGONQUEST_API_URL ?? "http://127.0.0.1:8080";
const proxy = { "/api": { target: api, changeOrigin: false } };
// Nomes extras aceitos no cabeçalho Host (ex.: o endereço do Tailscale Serve). Sem a variável, só localhost e IPs,
// o que protege contra DNS rebinding. Só nomes de host simples: nada de curinga solto.
const allowedHosts = (process.env.JOBDRAGONQUEST_ALLOWED_HOSTS ?? "")
  .split(",")
  .map((host) => host.trim().toLowerCase())
  .filter((host) => /^\.?[a-z0-9-]+(\.[a-z0-9-]+)+$/.test(host));

const mcpSource = fileURLToPath(new URL("../jobdragonquest-mcp/src", import.meta.url));
const zod = fileURLToPath(new URL("./node_modules/zod", import.meta.url));

export default defineConfig({
  // Versão do painel, comparada com a da API para avisar quando há atualização.
  define: { __APP_VERSION__: JSON.stringify(process.env.npm_package_version ?? "dev") },
  plugins: [react(), contentSecurityPolicy],
  resolve: { alias: { "@mcp": mcpSource, zod } },
  server: { host: "127.0.0.1", port: 5173, strictPort: true, proxy, allowedHosts, fs: { allow: [".", mcpSource] } },
  preview: { host: "127.0.0.1", port: 5173, strictPort: true, proxy, allowedHosts },
  build: { sourcemap: false, target: "es2022" },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["src/test-setup.ts"],
    restoreMocks: true,
    // Os testes do Playwright (e2e/) rodam pelo npm run e2e, contra o sistema de verdade.
    exclude: ["e2e/**", "node_modules/**", "dist/**"],
    testTimeout: 30_000,
    // No GitHub Actions também grava o relatório JUnit que o CI publica e resume (scripts/resumo-testes.mjs).
    reporters: process.env.GITHUB_ACTIONS === "true" ? ["default", "junit"] : ["default"],
    outputFile: { junit: "test-results/junit.xml" },
  },
});
