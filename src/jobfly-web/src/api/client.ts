// Cliente HTTP do painel.
// - O token de acesso fica só em memória (nunca em localStorage), então um XSS não o encontra guardado.
// - Em 401, tenta uma única renovação pelo cookie HttpOnly e repete a requisição. Renovações simultâneas
//   compartilham a mesma promessa, então o refresh token rotativo não é gasto duas vezes.
// - Erros da API (Problem Details) viram ApiError com o código estável para tradução.

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly errors: string[];

  constructor(status: number, code: string, message: string, errors: string[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.errors = errors;
  }
}

type TokenListener = (token: string | null) => void;

let accessToken: string | null = null;
let refreshing: Promise<string | null> | null = null;
const listeners = new Set<TokenListener>();

export function setAccessToken(token: string | null): void {
  accessToken = token;
  listeners.forEach((listener) => listener(token));
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function onTokenChange(listener: TokenListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Renova a sessão pelo cookie. Devolve o novo token ou null se a sessão acabou. */
export function refreshSession(): Promise<string | null> {
  if (!refreshing) {
    refreshing = fetch("/api/v1/auth/refresh", { method: "POST", credentials: "same-origin" })
      .then(async (response) => {
        if (!response.ok) {
          setAccessToken(null);
          return null;
        }
        const body = (await response.json()) as { accessToken: string };
        setAccessToken(body.accessToken);
        return body.accessToken;
      })
      .catch(() => {
        setAccessToken(null);
        return null;
      })
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  headers?: Record<string, string>;
  /** Para login, cadastro e refresh: não tenta renovar a sessão em 401. */
  anonymous?: boolean;
}

export interface ApiResponse<T> {
  data: T;
  status: number;
  headers: Headers;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
  if (!path.startsWith("/api/")) {
    throw new Error("Caminho de API inválido");
  }
  const send = () => {
    const headers: Record<string, string> = { Accept: "application/json", ...options.headers };
    if (options.body !== undefined) {
      headers["Content-Type"] = "application/json";
    }
    if (accessToken && !options.anonymous) {
      headers.Authorization = `Bearer ${accessToken}`;
    }
    return fetch(path, {
      method: options.method ?? "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: "same-origin",
    });
  };

  let response = await send();
  if (response.status === 401 && !options.anonymous) {
    const renewed = await refreshSession();
    if (renewed) {
      response = await send();
    }
  }
  return parse<T>(response);
}

async function parse<T>(response: Response): Promise<ApiResponse<T>> {
  const text = await response.text();
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = null;
    }
  }
  if (!response.ok) {
    const problem = (body ?? {}) as { code?: string; detail?: string; errors?: string[] };
    throw new ApiError(
      response.status,
      problem.code ?? `http_${response.status}`,
      problem.detail ?? response.statusText ?? "Erro",
      Array.isArray(problem.errors) ? problem.errors : [],
    );
  }
  return { data: body as T, status: response.status, headers: response.headers };
}
