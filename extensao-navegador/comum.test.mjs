// Testes das regras da extensão (node --test extensao-navegador/).
import assert from "node:assert/strict";
import { test } from "node:test";

import { apiValida, campoDoPerfil, chamar, decidirPreenchimento, ehSensivel, evidenciaDeEnvio, paginaValida, podeEnviar, tokenValido } from "./comum.js";

const previaDeExemplo = {
  name: "Ana Maria Souza",
  email: "ana@exemplo.test",
  phone: "555-0100",
  answers: [
    { category: "linkedin", question: "Link do seu LinkedIn", answer: "https://linkedin.com/in/ana", missing: null },
    { category: "salary", question: "Qual a sua pretensão salarial?", answer: "5000 BRL (monthly)", missing: null },
    { category: "experience", question: "Quanto tempo de experiência você tem com Java?", answer: "1 ano, 6 meses", missing: null },
    { category: "experience", question: "Quanto tempo de experiência você tem com Kotlin?", answer: null, missing: "O perfil não declara Kotlin." },
  ],
};

const campo = (indice, rotulo, extras = {}) => ({ indice, rotulo, contexto: rotulo, tipo: "text", vazio: true, sensivel: false, ...extras });

test("aceita só API local http sem caminho", () => {
  assert.equal(apiValida("http://127.0.0.1:8080"), "http://127.0.0.1:8080");
  assert.equal(apiValida(" http://localhost:9000/ "), "http://localhost:9000");
  assert.equal(apiValida("https://meu-site.onrender.com"), "https://meu-site.onrender.com");
  for (const ruim of ["http://exemplo.com", "http://192.168.0.10:8080", "http://127.0.0.1:8080/api", "http://u:s@127.0.0.1", "javascript:alert(1)", "http://127.0.0.1.evil.test"]) {
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

test("preenchimento nunca usa senha, CPF ou cartão e reconhece os campos comuns", () => {
  assert.equal(campoDoPerfil("Senha"), null);
  assert.equal(campoDoPerfil("CPF do candidato"), null);
  assert.equal(campoDoPerfil("Número do cartão"), null);
  assert.equal(campoDoPerfil("Your LinkedIn profile"), "linkedin");
  assert.equal(campoDoPerfil("E-mail"), "email");
  assert.equal(campoDoPerfil("Celular"), "telefone");
});

test("sensíveis nunca são preenchidos: documento, deficiência, raça, gênero, orientação e consentimento", () => {
  for (const rotulo of ["CPF", "Qual é o seu CPF?", "Você é uma pessoa com deficiência?", "Raça ou etnia", "Gênero", "Orientação sexual", "Consentimento - Diversidade e Inclusão (D&I)", "Do you live with any disabilities?", "Which racial/ethnic group best represents you?"]) {
    assert.equal(ehSensivel(rotulo), true, rotulo);
  }
  assert.equal(ehSensivel("Nome"), false);
  assert.equal(ehSensivel("Link do seu LinkedIn"), false);
  const campos = [campo(0, "CPF"), campo(1, "Você é uma pessoa com deficiência?"), campo(2, "Gênero"), campo(3, "Nome")];
  assert.deepEqual(decidirPreenchimento(campos, previaDeExemplo), [{ indice: 3, valor: "Ana" }]);
});

test("preenche nome, sobrenome, e-mail, telefone, links e respostas do perfil, só em campo vazio", () => {
  const campos = [
    campo(0, "Nome*"),
    campo(1, "Sobrenome*"),
    campo(2, "E-mail*"),
    campo(3, "Telefone"),
    campo(4, "LinkedIn Profile"),
    campo(5, "Qual a sua pretensão salarial?"),
    campo(6, "Quanto tempo de experiência você tem com Java?"),
    campo(7, "Quanto tempo de experiência você tem com Kotlin?"),
    campo(8, "Nome da empresa"),
    campo(9, "Nome", { vazio: false }),
    campo(10, "E-mail", { tipo: "select" }),
  ];
  assert.deepEqual(decidirPreenchimento(campos, previaDeExemplo), [
    { indice: 0, valor: "Ana" },
    { indice: 1, valor: "Maria Souza" },
    { indice: 2, valor: "ana@exemplo.test" },
    { indice: 3, valor: "555-0100" },
    { indice: 4, valor: "https://linkedin.com/in/ana" },
    { indice: 5, valor: "5000 BRL (monthly)" },
    { indice: 6, valor: "1 ano, 6 meses" },
  ]);
});

test("sem dado no perfil o campo fica vazio, e rótulo de vaga não vira nome da pessoa", () => {
  const semContato = { name: null, answers: [] };
  assert.deepEqual(decidirPreenchimento([campo(0, "Nome"), campo(1, "E-mail"), campo(2, "Quando você pode começar?")], semContato), []);
});

test("envio só sai com confirmação, sem CAPTCHA e com campos sensíveis preenchidos pela pessoa", () => {
  const campos = [{ indice: 0, sensivel: false, vazio: false }];
  assert.equal(podeEnviar({ campos, captcha: false, confirmado: false }).ok, false);
  assert.equal(podeEnviar({ campos, captcha: true, confirmado: true }).ok, false);
  assert.equal(podeEnviar({ campos: [{ indice: 1, sensivel: true, vazio: true }], captcha: false, confirmado: true }).ok, false);
  assert.deepEqual(podeEnviar({ campos, captcha: false, confirmado: true }), { ok: true, motivo: null });
});

test("envio automático dispensa a confirmação de cada vaga, mas não passa por CAPTCHA nem campo sensível vazio", () => {
  const campos = [{ indice: 0, sensivel: false, vazio: false }];
  assert.deepEqual(podeEnviar({ campos, captcha: false, confirmado: false, automatico: true }), { ok: true, motivo: null });
  assert.equal(podeEnviar({ campos, captcha: true, confirmado: false, automatico: true }).ok, false);
  assert.equal(podeEnviar({ campos: [{ indice: 1, sensivel: true, vazio: true }], captcha: false, automatico: true }).ok, false);
});

test("evidência do envio automático diz que saiu pela liga da pessoa", () => {
  const texto = evidenciaDeEnvio("https://empresa.test/vagas/2", new Date("2026-11-12T10:00:00Z"), true);
  assert.match(texto, /envio automático que a pessoa deixou ligado/);
  assert.match(texto, /Conferir em: https:\/\/empresa\.test\/vagas\/2$/);
});

test("evidência do envio traz data, hora e o endereço para conferir", () => {
  const texto = evidenciaDeEnvio("https://empresa.test/vagas/1", new Date("2026-11-12T10:00:00Z"));
  assert.match(texto, /2026-11-12T10:00:00\.000Z/);
  assert.match(texto, /Conferir em: https:\/\/empresa\.test\/vagas\/1$/);
});
