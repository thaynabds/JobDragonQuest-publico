// Cliente HTTP da API do JobDragonQuest usado pelas ferramentas. Caminhos montados só por segmentos codificados, sem
// redirecionamento, com tempo e tamanho de resposta limitados. Mensagens de erro nunca incluem o token.
import type { Config } from "./config.js";

export const MAX_RESPONSE_BYTES = 1024 * 1024;
const TIMEOUT_MS = 15_000;

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

type Query = Record<string, string | number | boolean | undefined | null>;

/** Monta /api/v1/<segmentos> codificando cada segmento: um id com "../" não muda de rota. */
export function apiPath(segments: readonly string[], query: Query = {}): string {
  const path = "/api/v1/" + segments.map((segment) => encodeURIComponent(segment)).join("/");
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  }
  const text = search.toString();
  return text ? `${path}?${text}` : path;
}

export interface Client {
  request<T>(method: "GET" | "POST" | "PUT", path: string, body?: unknown, headers?: Record<string, string>): Promise<T>;
}

export function createClient(config: Config, fetchImpl: typeof fetch = fetch): Client {
  return {
    async request<T>(method: "GET" | "POST" | "PUT", path: string, body?: unknown, headers: Record<string, string> = {}) {
      if (!path.startsWith("/api/v1/")) {
        throw new Error("Caminho de API inválido");
      }
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
      let response: Response;
      try {
        response = await fetchImpl(config.apiUrl + path, {
          method,
          redirect: "error",
          signal: controller.signal,
          headers: {
            Accept: "application/json",
            Authorization: `Bearer ${config.token}`,
            ...(body === undefined ? {} : { "Content-Type": "application/json" }),
            ...headers,
          },
          body: body === undefined ? undefined : JSON.stringify(body),
        });
      } catch {
        throw new ApiError(0, "unreachable", "Não consegui falar com o JobDragonQuest. Ele está ligado? (scripts/iniciar.sh)");
      } finally {
        clearTimeout(timer);
      }
      const length = Number(response.headers.get("content-length") ?? "0");
      if (length > MAX_RESPONSE_BYTES) {
        throw new ApiError(response.status, "too_large", "Resposta grande demais");
      }
      const text = await response.text();
      if (text.length > MAX_RESPONSE_BYTES) {
        throw new ApiError(response.status, "too_large", "Resposta grande demais");
      }
      if (!response.ok) {
        let code = "http_" + response.status;
        let message = response.status === 401 ? "Token inválido, vencido ou revogado: crie outro em Configurações no painel" : `Erro ${response.status}`;
        try {
          const problem = JSON.parse(text) as { code?: unknown; detail?: unknown };
          if (typeof problem.code === "string") code = problem.code;
          if (typeof problem.detail === "string" && response.status !== 401) message = problem.detail.slice(0, 300);
        } catch {
          // Corpo sem JSON: fica a mensagem genérica.
        }
        throw new ApiError(response.status, code, message);
      }
      return (text ? JSON.parse(text) : null) as T;
    },
  };
}
