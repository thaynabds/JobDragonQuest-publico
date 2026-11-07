import { Client as McpClient } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { chmodSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { describe, expect, it, vi } from "vitest";

import { ApiError, apiPath, createClient, MAX_RESPONSE_BYTES, type Client } from "./client.js";
import { ConfigError, loadConfig } from "./config.js";
import { buildServer } from "./server.js";
import { formatResult, MAX_OUTPUT_CHARS, TOOLS, UNTRUSTED_NOTE } from "./tools.js";

const TOKEN = "jfp_" + "a".repeat(43);
const ID = "3f2b7c1e-9d4a-4b8e-8f00-1234567890ab";

describe("configuração", () => {
  it("aceita só a API local por HTTP ou remota por HTTPS com liberação", () => {
    expect(loadConfig({ JOBFLY_TOKEN: TOKEN })).toEqual({ apiUrl: "http://127.0.0.1:8080", token: TOKEN });
    expect(loadConfig({ JOBFLY_TOKEN: TOKEN, JOBFLY_API_URL: "http://localhost:9090/" }).apiUrl).toBe("http://localhost:9090");
    expect(loadConfig({ JOBFLY_TOKEN: TOKEN, JOBFLY_API_URL: "https://jobfly.example.com", JOBFLY_ALLOW_REMOTE: "1" }).apiUrl).toBe(
      "https://jobfly.example.com",
    );
    for (const url of ["http://evil.example.com", "https://evil.example.com", "http://127.0.0.1.evil.com", "http://user:pass@127.0.0.1:8080", "file:///etc/passwd", "http://127.0.0.1:8080/?x=1"]) {
      expect(() => loadConfig({ JOBFLY_TOKEN: TOKEN, JOBFLY_API_URL: url }), url).toThrow(ConfigError);
    }
  });

  it("recusa token ausente ou malformado sem repetir o valor no erro", () => {
    const secret = "jfp_segredo-curto";
    try {
      loadConfig({ JOBFLY_TOKEN: secret });
      expect.unreachable();
    } catch (error) {
      expect(error).toBeInstanceOf(ConfigError);
      expect(String((error as Error).message)).not.toContain(secret);
    }
    expect(() => loadConfig({})).toThrow(ConfigError);
  });
});

describe("token em arquivo", () => {
  it("lê o token de um arquivo 600 e recusa arquivo legível por outras contas", () => {
    const dir = mkdtempSync(join(tmpdir(), "jobfly-mcp-"));
    try {
      const file = join(dir, "token");
      writeFileSync(file, TOKEN + "\n");
      chmodSync(file, 0o600);
      expect(loadConfig({ JOBFLY_TOKEN_FILE: file }).token).toBe(TOKEN);
      chmodSync(file, 0o644);
      expect(() => loadConfig({ JOBFLY_TOKEN_FILE: file })).toThrow("chmod 600");
      expect(() => loadConfig({ JOBFLY_TOKEN_FILE: join(dir, "nao-existe") })).toThrow(ConfigError);
      writeFileSync(file, "x".repeat(1000));
      chmodSync(file, 0o600);
      expect(() => loadConfig({ JOBFLY_TOKEN_FILE: file })).toThrow("arquivo pequeno");
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });
});

describe("cliente da API", () => {
  it("codifica cada segmento: um id com ../ não muda de rota", () => {
    expect(apiPath(["applications", "../tokens", "notes"])).toBe("/api/v1/applications/..%2Ftokens/notes");
    expect(apiPath(["jobs"], { page: 0, sourceId: undefined, size: 20 })).toBe("/api/v1/jobs?page=0&size=20");
  });

  it("envia o token só no cabeçalho, sem seguir redirecionamento", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response("[]", { status: 200 }));
    await createClient({ apiUrl: "http://127.0.0.1:8080", token: TOKEN }, fetchImpl).request("GET", "/api/v1/profiles");
    const [url, init] = fetchImpl.mock.calls[0]!;
    expect(url).toBe("http://127.0.0.1:8080/api/v1/profiles");
    expect(init.redirect).toBe("error");
    expect(init.headers.Authorization).toBe(`Bearer ${TOKEN}`);
  });

  const failure = (promise: Promise<unknown>): Promise<ApiError> =>
    promise.then(
      () => {
        throw new Error("esperava erro");
      },
      (error: unknown) => error as ApiError,
    );

  it("erros não vazam o token e respostas gigantes são recusadas", async () => {
    const client = (response: Response | Error) =>
      createClient({ apiUrl: "http://127.0.0.1:8080", token: TOKEN }, vi.fn().mockImplementation(() => (response instanceof Error ? Promise.reject(response) : Promise.resolve(response))));
    const unauthorized = await failure(
      client(new Response(JSON.stringify({ code: "invalid_token", detail: TOKEN }), { status: 401 })).request("GET", "/api/v1/profiles"),
    );
    expect(unauthorized.status).toBe(401);
    expect(unauthorized.message).not.toContain(TOKEN);
    const offline = await failure(client(new TypeError(`fetch failed ${TOKEN}`)).request("GET", "/api/v1/profiles"));
    expect(offline.code).toBe("unreachable");
    expect(offline.message).not.toContain(TOKEN);
    const huge = await failure(client(new Response("x".repeat(MAX_RESPONSE_BYTES + 1), { status: 200 })).request("GET", "/api/v1/jobs"));
    expect(huge.code).toBe("too_large");
    await expect(client(new Response("{}")).request("GET", "/outra/rota")).rejects.toThrow("Caminho de API inválido");
  });
});

describe("ferramentas", () => {
  const byName = Object.fromEntries(TOOLS.map((tool) => [tool.name, tool]));

  it("não há ferramenta que apague dados ou mexa em tokens", () => {
    expect(TOOLS.length).toBeGreaterThanOrEqual(17);
    for (const tool of TOOLS) {
      expect(tool.name).toMatch(/^jobfly_[a-z_]+$/);
      expect(tool.name).not.toMatch(/apagar|excluir|delete|remover|token|senha/);
    }
  });

  it("valida ids, URLs, enums e tamanhos", () => {
    expect(byName.jobfly_ver_vaga!.schema.safeParse({ id: "../tokens" }).success).toBe(false);
    expect(byName.jobfly_ver_vaga!.schema.safeParse({ id: ID }).success).toBe(true);
    expect(byName.jobfly_cadastrar_vaga!.schema.safeParse({ empresa: "A", cargo: "B", url: "javascript:alert(1)" }).success).toBe(false);
    expect(byName.jobfly_cadastrar_vaga!.schema.safeParse({ empresa: "A", cargo: "B", descricao: "x".repeat(20_001) }).success).toBe(false);
    expect(byName.jobfly_atualizar_etapa!.schema.safeParse({ id: ID, etapa: "DELETED", evidencia: "x", confianca: 0.5 }).success).toBe(false);
    expect(byName.jobfly_atualizar_etapa!.schema.safeParse({ id: ID, etapa: "SCREENING", evidencia: "x", confianca: 2 }).success).toBe(false);
    expect(byName.jobfly_cadastrar_fontes!.schema.safeParse({ perfilIds: [ID], frequenciaMinutos: 120, fontes: [{ nome: "x", tipo: "GREENHOUSE", identificador: "../etc" }] }).success).toBe(false);
  });

  it("mudança de etapa sempre entra como agente e cadastro usa chave de idempotência", async () => {
    const request = vi.fn().mockResolvedValue({});
    const client: Client = { request };
    await byName.jobfly_atualizar_etapa!.run(client, { id: ID, etapa: "SCREENING", evidencia: "E-mail", confianca: 0.6 } as never);
    expect(request).toHaveBeenCalledWith("POST", `/api/v1/applications/${ID}/transitions`, expect.objectContaining({ source: "AGENT", toState: "SCREENING", confidence: 0.6 }));
    await byName.jobfly_registrar_candidatura!.run(client, { empresa: "Acme", cargo: "Dev" } as never);
    expect(request.mock.calls[1]![3]).toEqual({ "Idempotency-Key": expect.stringMatching(/^mcp-[0-9a-f-]{36}$/) });
  });

  it("marca os dados como não confiáveis e corta respostas longas", () => {
    expect(formatResult({ a: 1 })).toContain(UNTRUSTED_NOTE);
    const long = formatResult({ texto: "x".repeat(MAX_OUTPUT_CHARS * 2) });
    expect(long.length).toBeLessThan(MAX_OUTPUT_CHARS + 100);
    expect(long).toContain("resposta cortada");
  });
});

describe("servidor MCP de ponta a ponta (em memória)", () => {
  it("lista as ferramentas e responde chamadas, com erro legível para argumentos inválidos", async () => {
    const request = vi.fn().mockResolvedValue({ jobsFound: 3 });
    const server = buildServer({ request }, "0.3.0");
    const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
    await server.connect(serverTransport);
    const client = new McpClient({ name: "teste", version: "1.0.0" });
    await client.connect(clientTransport);

    const { tools } = await client.listTools();
    expect(tools.map((t) => t.name)).toContain("jobfly_resumo");
    expect(tools.find((t) => t.name === "jobfly_resumo")!.annotations?.readOnlyHint).toBe(true);

    const result = await client.callTool({ name: "jobfly_resumo", arguments: {} });
    expect(JSON.stringify(result.content)).toContain("jobsFound");
    expect(request).toHaveBeenCalledWith("GET", "/api/v1/dashboard/summary");

    const invalid = await client.callTool({ name: "jobfly_ver_vaga", arguments: { id: "../tokens" } });
    expect(invalid.isError).toBe(true);
    expect(request).toHaveBeenCalledTimes(1);

    request.mockRejectedValueOnce(new ApiError(404, "not_found", "Vaga não encontrado"));
    const missing = await client.callTool({ name: "jobfly_ver_vaga", arguments: { id: ID } });
    expect(missing.isError).toBe(true);
    expect(JSON.stringify(missing.content)).toContain("not_found");
    await client.close();
  });
});
