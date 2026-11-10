// Escolha do modelo para o chat com a IA. A chave da pessoa nunca vai para a API do JobDragonQuest: fica em memória
// e, só se ela marcar "lembrar neste navegador", no localStorage deste navegador.

export type Provider = "ollama" | "anthropic" | "openai";

export interface AiSettings {
  provider: Provider;
  model: string;
  apiKey: string;
  ollamaUrl: string;
  remember: boolean;
}

export const DEFAULT_MODELS: Record<Provider, string> = {
  ollama: "qwen3",
  anthropic: "claude-sonnet-5-5",
  openai: "",
};

export const DEFAULT_SETTINGS: AiSettings = {
  provider: "ollama",
  model: DEFAULT_MODELS.ollama,
  apiKey: "",
  ollamaUrl: "http://127.0.0.1:11434",
  remember: false,
};

const STORAGE_KEY = "jobdragonquest.ai";
let inMemory: AiSettings | null = null;

/** Ollama só no próprio computador: aceitar qualquer endereço deixaria o painel chamar hosts da rede interna. */
export function isLoopbackUrl(value: string): boolean {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return false;
  }
  return url.protocol === "http:" && (url.hostname === "127.0.0.1" || url.hostname === "localhost") && url.pathname === "/" && !url.search && !url.username && !url.password;
}

const MODEL = /^[A-Za-z0-9._:/-]{1,100}$/;

/** O que falta para conversar, como chave de tradução; null quando está pronto. */
export function settingsProblem(settings: AiSettings): "assistant.ai.needModel" | "assistant.ai.needKey" | "assistant.ai.badUrl" | null {
  if (!MODEL.test(settings.model)) return "assistant.ai.needModel";
  if (settings.provider === "ollama") return isLoopbackUrl(settings.ollamaUrl) ? null : "assistant.ai.badUrl";
  return settings.apiKey.trim().length >= 10 && settings.apiKey.length <= 300 ? null : "assistant.ai.needKey";
}

function sanitize(raw: unknown): AiSettings | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Partial<AiSettings>;
  const provider: Provider = value.provider === "anthropic" || value.provider === "openai" ? value.provider : "ollama";
  return {
    provider,
    model: typeof value.model === "string" ? value.model.slice(0, 100) : DEFAULT_MODELS[provider],
    apiKey: typeof value.apiKey === "string" ? value.apiKey.slice(0, 300) : "",
    ollamaUrl: typeof value.ollamaUrl === "string" && isLoopbackUrl(value.ollamaUrl) ? value.ollamaUrl : DEFAULT_SETTINGS.ollamaUrl,
    remember: true,
  };
}

/** A pessoa já escolheu a IA (nesta sessão ou lembrada no navegador)? Sem escolha, nada é enviado a provedor algum. */
export function isConfigured(): boolean {
  if (inMemory) return true;
  try {
    return localStorage.getItem(STORAGE_KEY) !== null;
  } catch {
    return false;
  }
}

export function loadSettings(): AiSettings {
  if (inMemory) return inMemory;
  try {
    const saved = sanitize(JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "null"));
    if (saved) return saved;
  } catch {
    // Armazenamento bloqueado ou corrompido: volta ao padrão.
  }
  return DEFAULT_SETTINGS;
}

export function saveSettings(settings: AiSettings): void {
  inMemory = settings;
  try {
    if (settings.remember) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Sem armazenamento: fica só em memória.
  }
}

/** Ao sair da conta a chave some também deste navegador. */
export function forgetSettings(): void {
  inMemory = null;
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Nada guardado.
  }
}
