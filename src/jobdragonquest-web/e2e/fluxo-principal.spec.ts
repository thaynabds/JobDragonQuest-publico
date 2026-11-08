// Fluxo principal no navegador (issue #166): criar conta, criar perfil, registrar uma candidatura, atualizar a etapa
// com evidência e conferir que outra conta não vê nada disso.
import { expect, test, type Page } from "@playwright/test";

const SENHA = "senha-forte-de-teste-123";

async function criarConta(page: Page, nome: string) {
  await page.goto("/");
  await page.getByRole("button", { name: "Não tem conta? Crie uma." }).click();
  await page.getByLabel("Seu nome").fill(nome);
  await page.getByLabel("E-mail").fill(`e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@teste.dev`);
  await page.getByLabel("Senha").fill(SENHA);
  await page.getByRole("button", { name: "Criar conta" }).click();
  await expect(page.getByRole("heading", { name: "Visão geral" })).toBeVisible();
}

test("conta, perfil, candidatura e etapa com evidência, isolados de outra conta", async ({ page, browser }) => {
  await criarConta(page, "Pessoa E2E");

  // Perfil profissional.
  await page.goto("/perfis/novo");
  await page.getByRole("textbox", { name: /^Nome do perfil/ }).fill("Desenvolvedora Java");
  await page.getByRole("combobox", { name: "Senioridade" }).selectOption("JUNIOR");
  await page.getByRole("button", { name: "Salvar" }).click();
  await expect(page).toHaveURL(/\/perfis\/(?!novo)[0-9a-f-]{36}$/);

  // Candidatura registrada à mão.
  await page.goto("/candidaturas");
  await page.getByRole("button", { name: "Registrar candidatura externa" }).click();
  await page.getByLabel("Empresa").fill("Acme E2E");
  await page.getByLabel("Cargo").fill("Desenvolvedora Java Júnior");
  await page.getByRole("button", { name: "Salvar" }).click();
  await expect(page.getByText("Candidatura registrada.")).toBeVisible();

  // Detalhe: muda a etapa para "Enviada" com evidência e confere no histórico depois de recarregar.
  await page.getByRole("link", { name: /Acme E2E/ }).first().click();
  const etapa = page.locator("form", { has: page.getByLabel("O que aconteceu (evidência)") });
  await etapa.getByLabel("Etapa atual").selectOption("SUBMITTED");
  await etapa.getByLabel("O que aconteceu (evidência)").fill("Enviei pelo site da empresa");
  await etapa.getByRole("button", { name: "Salvar" }).click();
  await page.reload();
  await expect(page.getByText("Enviei pelo site da empresa")).toBeVisible();

  // Isolamento: outra conta, em outro navegador, não vê a candidatura.
  const outra = await browser.newContext();
  const intrusa = await outra.newPage();
  await criarConta(intrusa, "Outra Pessoa");
  await intrusa.goto("/candidaturas");
  await expect(intrusa.getByRole("heading", { name: "Candidaturas" })).toBeVisible();
  await expect(intrusa.getByText("Acme E2E")).toHaveCount(0);
  await outra.close();
});
