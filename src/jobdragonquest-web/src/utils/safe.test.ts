import { describe, expect, it } from "vitest";

import { localInputToIso, newIdempotencyKey, safeUrl, splitList } from "./safe";

describe("utilitários de segurança", () => {
  it("só aceita links http e https", () => {
    expect(safeUrl("https://linkedin.com/jobs/view/1")).toBe("https://linkedin.com/jobs/view/1");
    expect(safeUrl("javascript:alert(1)")).toBeNull();
    expect(safeUrl("data:text/html,<script>")).toBeNull();
    expect(safeUrl("não é url")).toBeNull();
    expect(safeUrl(null)).toBeNull();
  });

  it("divide listas sem vazios nem repetidos", () => {
    expect(splitList(" Java, Spring Boot,, Java ,")).toEqual(["Java", "Spring Boot"]);
  });

  it("gera chave de idempotência no formato aceito pela API", () => {
    const key = newIdempotencyKey("ext");
    expect(key).toMatch(/^[A-Za-z0-9_-]{8,100}$/);
    expect(newIdempotencyKey("ext")).not.toBe(key);
  });

  it("converte data local para ISO", () => {
    expect(localInputToIso("")).toBeNull();
    expect(localInputToIso("2026-11-04T15:00")).toMatch(/^2026-11-04T\d{2}:00:00\.000Z$/);
  });
});
