// Testes das regras da extensão (node --test extensao-navegador/).
import assert from "node:assert/strict";
import { test } from "node:test";

import { apiValida, chamar, paginaValida, tokenValido } from "./comum.js";

test("aceita só API local http sem caminho", () => {
  assert.equal(apiValida("http://127.0.0.1:8080"), "http://127.0.0.1:8080");
  assert.equal(apiValida(" http://localhost:9000/ "), "http://localhost:9000");
  for (const ruim of ["https://exemplo.com", "http://192.168.0.10:8080", "http://127.0.0.1:8080/api", "http://u:s@127.0.0.1", "javascript:alert(1)", "http://127.0.0.1.evil.test"]) {
    assert.equal(apiValida(ruim), null, ruim);
  }
});

test("token pessoal com formato do JobDragonQuest", () => {
  assert.equal(tokenValido("jdq_" + "a".repeat(43)), "jdq_" + "a".repeat(43));
  assert.equal(tokenValido("Bearer jdq_x"), null);
  assert.equal(tokenValido("jdq_<script>alert(1)</script>aaaaaaaaaaaa"), null);
});

test("só páginas http(s) são enviadas", () => {
  assert.equal(paginaValida("https://empresa.test/vaga/1"), "https://empresa.test/vaga/1");
  assert.equal(paginaValida("chrome://settings"), null);
  assert.equal(paginaValida("file:///etc/passwd"), null);
});

test("chamada usa o token no cabeçalho, sem cookies e sem seguir redirecionamento", async () => {
  let pedido = null;
  globalThis.fetch = async (url, opcoes) => {
    pedido = { url, opcoes };
    return new Response(JSON.stringify({ ok: true }), { status: 200 });
  };
  const resposta = await chamar({ api: "http://127.0.0.1:8080", token: "jdq_" + "b".repeat(43) }, "/api/v1/jobs/from-link", { url: "https://e.test" });
  assert.deepEqual(resposta, { ok: true });
  assert.equal(pedido.url, "http://127.0.0.1:8080/api/v1/jobs/from-link");
  assert.equal(pedido.opcoes.credentials, "omit");
  assert.equal(pedido.opcoes.redirect, "error");
  assert.equal(pedido.opcoes.headers.Authorization, "Bearer jdq_" + "b".repeat(43));
  globalThis.fetch = async () => new Response(JSON.stringify({ code: "link_blocked", detail: "Bloqueado" }), { status: 400 });
  await assert.rejects(() => chamar({ api: "http://127.0.0.1:8080", token: "t" }, "/x", {}), (erro) => erro.code === "link_blocked");
});
