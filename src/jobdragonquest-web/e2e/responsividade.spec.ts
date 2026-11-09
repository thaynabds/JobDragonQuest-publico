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

  const rotas = ["/", "/candidaturas", "/revisao", "/vagas", `/vagas/${dados.vaga}`, "/fontes", "/perfis", "/perfis/novo", `/perfis/${dados.perfil}`, `/perfis/${dados.perfil}/curriculo`, "/carreira", "/configuracoes"];
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
  await page.keyboard.press("Escape");
  await expect(page.getByRole("link", { name: "Candidaturas" })).toBeHidden();

  expect(problemas).toEqual([]);
});
