/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { defineConfig, type Plugin } from "vite";

// CSP só no build de produção: o modo de desenvolvimento do Vite injeta um script inline (React Refresh).
const CSP =
  "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; " +
  "base-uri 'self'; form-action 'self'; object-src 'none'";
// frame-ancestors não vale em <meta>: o navegador ignora. A proteção contra frames fica para o proxy de produção.
const contentSecurityPolicy: Plugin = {
  name: "jobfly-csp",
  apply: "build",
  transformIndexHtml: (html) =>
    html.replace("<head>", `<head>\n    <meta http-equiv="Content-Security-Policy" content="${CSP}" />`),
};

// O painel fala com a API pelo mesmo endereço (proxy /api), então o cookie de sessão fica restrito a esta origem.
// Servidor e preview escutam só em 127.0.0.1: outros aparelhos da rede não alcançam o painel.
const api = process.env.JOBFLY_API_URL ?? "http://127.0.0.1:8080";
const proxy = { "/api": { target: api, changeOrigin: false } };

export default defineConfig({
  plugins: [react(), contentSecurityPolicy],
  server: { host: "127.0.0.1", port: 5173, strictPort: true, proxy },
  preview: { host: "127.0.0.1", port: 5173, strictPort: true, proxy },
  build: { sourcemap: false, target: "es2022" },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["src/test-setup.ts"],
    restoreMocks: true,
    testTimeout: 30_000,
  },
});
