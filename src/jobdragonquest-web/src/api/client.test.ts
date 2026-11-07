import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError, getAccessToken, refreshSession, request, setAccessToken } from "./client";

const json = (status: number, body: unknown) =>
  new Response(body === null ? "" : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

afterEach(() => {
  setAccessToken(null);
  vi.unstubAllGlobals();
});

describe("cliente da API", () => {
  it("envia o token e renova a sessão uma única vez em 401", async () => {
    setAccessToken("velho");
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(401, { code: "unauthorized" }))
      .mockResolvedValueOnce(json(200, { accessToken: "novo" }))
      .mockResolvedValueOnce(json(200, { ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await request<{ ok: boolean }>("/api/v1/me");

    expect(response.data.ok).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[0]?.[1]?.headers.Authorization).toBe("Bearer velho");
    expect(fetchMock.mock.calls[1]?.[0]).toBe("/api/v1/auth/refresh");
    expect(fetchMock.mock.calls[2]?.[1]?.headers.Authorization).toBe("Bearer novo");
    expect(getAccessToken()).toBe("novo");
  });

  it("renovações simultâneas usam uma única chamada (o refresh token rotativo não é gasto duas vezes)", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, { accessToken: "x" }));
    vi.stubGlobal("fetch", fetchMock);

    const [a, b] = await Promise.all([refreshSession(), refreshSession()]);

    expect(a).toBe("x");
    expect(b).toBe("x");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("sessão expirada limpa o token e devolve o erro da API", async () => {
    setAccessToken("velho");
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValueOnce(json(401, { code: "unauthorized" })).mockResolvedValueOnce(json(401, { code: "invalid_session" })).mockResolvedValueOnce(json(401, { code: "unauthorized", detail: "Autenticação necessária" })),
    );

    await expect(request("/api/v1/me")).rejects.toMatchObject({ status: 401, code: "unauthorized" });
    expect(getAccessToken()).toBeNull();
  });

  it("converte Problem Details em ApiError com código e erros de validação", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(400, { code: "validation_failed", detail: "Dados inválidos", errors: ["name: obrigatório"] })));

    const error = await request("/api/v1/profiles", { method: "POST", body: {} }).catch((err: unknown) => err);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe("validation_failed");
    expect((error as ApiError).errors).toEqual(["name: obrigatório"]);
  });

  it("recusa caminhos fora da API", async () => {
    await expect(request("https://evil.example/api")).rejects.toThrow("Caminho de API inválido");
  });
});
