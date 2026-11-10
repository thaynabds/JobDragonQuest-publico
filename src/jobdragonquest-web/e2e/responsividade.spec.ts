// Nenhuma tela pode ter rolagem horizontal da página, do celular pequeno ao monitor largo (issue de responsividade).
import { expect, test, type Page } from "@playwright/test";

const LARGURAS = [320, 375, 768, 1024, 1440];

async function estouros(page: Page) {
  return page.evaluate(() => {
    const largura = document.documentElement.clientWidth;
    const fora: string[] = [];
    document.querySelectorAll("body *").forEach((elemento) => {
      const caixa = elemento.getBoundingClientRect();
      const estilo = getComputedStyle(elemento);
      if (!caixa.width || caixa.right <= largura + 1 || estilo.visibility === "hidden") return;
      for (let pai = elemento.parentElement; pai; pai = pai.parentElement) {
        const rolagem = getComputedStyle(pai).overflowX;
        if (rolagem === "auto" || rolagem === "scroll") return;
      }
      fora.push(`${elemento.tagName.toLowerCase()}.${String(elemento.getAttribute("class") ?? "").split(" ")[0]}:${Math.round(caixa.right)}`);
    });
    return { largura, pagina: document.documentElement.scrollWidth, fora: [...new Set(fora)].slice(0, 10) };
  });
}

test("todas as telas cabem em todas as larguras", async ({ page }) => {
  test.setTimeout(420_000);
  const problemas: string[] = [];
  await page.setViewportSize({ width: 320, height: 700 });
  await page.goto("/");
  const conferir = async (rota: string) => {
    for (const largura of LARGURAS) {
      await page.setViewportSize({ width: largura, height: 800 });
      await page.waitForTimeout(150);
      const medida = await estouros(page);
      if (medida.pagina > medida.largura + 1 || medida.fora.length > 0) problemas.push(`${rota} @${largura}: ${medida.pagina}/${medida.largura} ${medida.fora.join(" ")}`);
    }
  };
  await conferir("login");

  // Os links da tela de entrada continuam parecendo links, também com um tema do widget ligado.
  for (const tema of [null, "dark"]) {
    if (tema) {
      await page.evaluate((nome) => localStorage.setItem("a11y-widget-v2", JSON.stringify({ theme: nome })), tema);
      await page.reload();
      await expect(page.locator("body.aw-themed")).toHaveCount(1);
    }
    const estilos = await page.locator(".link-button").evaluateAll((els) => els.map((el) => {
      const s = getComputedStyle(el);
      return { fundo: s.backgroundColor, borda: s.borderTopWidth };
    }));
    if (estilos.length === 0) problemas.push("links da tela de entrada não encontrados");
    for (const estilo of estilos) {
      if (estilo.fundo !== "rgba(0, 0, 0, 0)" || estilo.borda !== "0px") problemas.push(`link com cara de botão (tema ${tema}): ${JSON.stringify(estilo)}`);
    }
  }
  await page.evaluate(() => localStorage.removeItem("a11y-widget-v2"));
  await page.reload();

  await page.getByRole("button", { name: "Não tem conta? Crie uma." }).click();
  await page.getByLabel("Seu nome").fill("Pessoa Responsiva");
  await page.getByLabel("E-mail").fill(`resp-${Date.now()}@teste.dev`);
  await page.locator('input[autocomplete="new-password"]').fill("senha-forte-de-teste-123");
  await page.getByRole("button", { name: "Criar conta" }).click();
  await expect(page.getByRole("heading", { name: "Visão geral" })).toBeVisible();

  // Dados para as tabelas, o quadro e o currículo não ficarem vazios, com textos longos de propósito.
  const longo = "Texto-bem-comprido-sem-espacos-".repeat(6);
  const dados = await page.evaluate(async (texto) => {
    const refresh = await fetch("/api/v1/auth/refresh", { method: "POST" });
    const { accessToken } = await refresh.json();
    const post = (url: string, body: unknown) =>
      fetch(url, { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` }, body: JSON.stringify(body) }).then((r) => r.json());
    const perfil = await post("/api/v1/profiles", { name: texto.slice(0, 100), seniority: "JUNIOR", skills: [{ name: "Java", level: 3 }], entries: [{ kind: "PROJECT", title: texto.slice(0, 150), description: texto }] });
    await post("/api/v1/sources/bulk", { profileIds: [perfil.id], sources: [{ name: texto.slice(0, 100), kind: "LINKEDIN", url: `https://www.linkedin.com/jobs/search/?keywords=${texto}` }, { name: "ATS", kind: "GREENHOUSE", identifier: "🐉 empresa" }] });
    const vaga = await post("/api/v1/jobs", { source: "MANUAL", url: "https://exemplo.dev/vaga/1", company: texto.slice(0, 100), title: texto.slice(0, 150), description: texto, workMode: "REMOTE", requirements: [{ kind: "MANDATORY", text: "Java", skill: "Java" }] });
    await post("/api/v1/applications", { origin: "MANUAL", platform: "LinkedIn", company: texto.slice(0, 100), title: texto.slice(0, 150) });
    return { perfil: perfil.id as string, vaga: vaga.id as string };
  }, longo);

  const rotas = ["/", "/assistentes", "/triagem", "/concursos", "/ajuda", "/candidaturas", "/revisao", "/vagas", `/vagas/${dados.vaga}`, "/fontes", "/perfis", "/perfis/novo", `/perfis/${dados.perfil}`, `/perfis/${dados.perfil}/curriculo`, "/carreira", "/configuracoes"];
  for (const rota of rotas) {
    await page.goto(rota);
    // Os avisos consultam a API o tempo todo: não dá para esperar a rede parar.
    await page.locator("h1, h3").first().waitFor();
    await page.waitForTimeout(1200);
    await conferir(rota);
  }

  // Menu aberto no celular.
  await page.setViewportSize({ width: 320, height: 700 });
  await page.goto("/");
  await page.getByRole("button", { name: /Menu/ }).click();
  await expect(page.getByRole("link", { name: "Candidaturas" })).toBeVisible();
  const menu = await estouros(page);
  if (menu.pagina > menu.largura + 1) problemas.push(`menu aberto: ${menu.pagina}/${menu.largura}`);
  const fundo = await page.locator(".sidebar").evaluate((el) => getComputedStyle(el).backgroundColor);
  const casou = /(?:\/|,)\s*([0-9.]+)\s*\)$/.exec(fundo);
  const alfa = Number(casou?.[1] ?? "1");
  if (!(alfa >= 0.85 && alfa < 1)) problemas.push(`menu lateral com opacidade ${alfa}`);
  await page.keyboard.press("Escape");
  await expect(page.getByRole("link", { name: "Candidaturas" })).toBeHidden();

  // O widget oficial de acessibilidade precisa estar na página.
  await page.goto("/");
  await expect(page.locator("#a11y-btn")).toBeVisible();

  // Painel de avisos aberto: fica dentro da janela e não cria rolagem na página.
  await page.goto("/");
  for (const largura of [...LARGURAS, 700]) {
    await page.setViewportSize({ width: largura, height: 700 });
    await page.locator(".bell-button").click();
    await expect(page.locator("#bell-panel")).toBeVisible();
    const caixa = await page.locator("#bell-panel").boundingBox();
    const medida = await estouros(page);
    if (!caixa || caixa.x < 0 || caixa.x + caixa.width > medida.largura || medida.pagina > medida.largura + 1) problemas.push(`avisos @${largura}: ${JSON.stringify(caixa)} ${medida.pagina}/${medida.largura}`);
    const fundoAvisos = await page.locator("#bell-panel").evaluate((el) => getComputedStyle(el).backgroundColor);
    const alfaAvisos = Number(/(?:\/|,)\s*([0-9.]+)\s*\)$/.exec(fundoAvisos)?.[1] ?? "1");
    if (!(alfaAvisos >= 0.85 && alfaAvisos < 1)) problemas.push(`painel de avisos com opacidade ${alfaAvisos} @${largura}`);
    await page.locator(".bell-button").click();
    await expect(page.locator("#bell-panel")).toBeHidden();
  }

  expect(problemas).toEqual([]);
});
