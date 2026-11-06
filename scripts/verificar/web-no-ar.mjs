// G6: o painel está no ar em 127.0.0.1:5173, com CSP do build de produção, encaminha /api para a API e não escuta na rede.
import { networkInterfaces } from "node:os";

import { check, finish } from "./lib.mjs";

const BASE = "http://127.0.0.1:5173";

async function get(url) {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(10_000) });
    return { status: response.status, text: await response.text(), auth: response.headers.get("www-authenticate") ?? "" };
  } catch (error) {
    return { status: 0, text: String(error?.cause?.code ?? error), auth: "" };
  }
}

const page = await get(`${BASE}/`);
check("painel responde 200", page.status === 200, page.status);
check("página do JobFly com o ponto de montagem", page.text.includes('<div id="root">') && page.text.includes("<title>JobFly</title>"));
check("CSP do build de produção presente", page.text.includes("Content-Security-Policy") && page.text.includes("script-src 'self'"));

const deep = await get(`${BASE}/fontes`);
check("rota do painel funciona ao recarregar (fallback SPA)", deep.status === 200 && deep.text.includes('<div id="root">'), deep.status);

const proxied = await get(`${BASE}/api/v1/me`);
check("proxy /api chega à API (401 com WWW-Authenticate: Bearer)", proxied.status === 401 && proxied.auth.startsWith("Bearer"),
  `${proxied.status} ${proxied.auth}`);

const health = await get(`${BASE}/api/v1/../../actuator/health`);
check("proxy não expõe nada fora de /api", health.status !== 200 || !health.text.includes('"status"'), health.status);

const lans = Object.values(networkInterfaces()).flat().filter((item) => item && item.family === "IPv4" && !item.internal);
check("há interface de rede para o teste negativo", lans.length > 0);
for (const lan of lans) {
  const external = await get(`http://${lan.address}:5173/`);
  check(`painel não escuta em ${lan.address}`, external.status === 0, external.status);
  const api = await get(`http://${lan.address}:8080/actuator/health`);
  check(`API não escuta em ${lan.address}`, api.status === 0, api.status);
}

finish("WEB_NO_AR_OK");
