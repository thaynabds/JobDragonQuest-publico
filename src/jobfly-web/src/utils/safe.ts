/** Só deixa virar link um endereço http(s). Qualquer outro esquema (javascript:, data:...) é descartado. */
export function safeUrl(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:" ? url.toString() : null;
  } catch {
    return null;
  }
}

/** Divide "a, b ,, c" em ["a", "b", "c"], sem repetidos. */
export function splitList(value: string): string[] {
  return [...new Set(value.split(",").map((item) => item.trim()).filter(Boolean))];
}

/** Chave de idempotência para criação: reenviar o mesmo formulário não duplica o registro. */
export function newIdempotencyKey(prefix: string): string {
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
  return `${prefix}-${random}`.replace(/[^A-Za-z0-9_-]/g, "").slice(0, 100);
}

/** Converte o valor de um input datetime-local (hora local) para ISO em UTC, ou null se vazio. */
export function localInputToIso(value: string): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
