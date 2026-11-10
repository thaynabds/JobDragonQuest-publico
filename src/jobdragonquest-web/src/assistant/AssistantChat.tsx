import { useEffect, useRef, useState, type FormEvent } from "react";

import { Button, Card, Field, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import type { MessageKey } from "../i18n/messages";
import { runAgent, SYSTEM_PROMPT, type AnyTool } from "./agent";
import { AiError, chat, type ChatMessage } from "./providers";
import { DEFAULT_MODELS, isConfigured, loadSettings, saveSettings, settingsProblem, type AiSettings, type Provider } from "./settings";

const MAX_INPUT = 4000;

interface PendingConfirm {
  tool: AnyTool;
  args: unknown;
  resolve: (allowed: boolean) => void;
}

function AiSettingsForm({ onSaved }: { onSaved: (settings: AiSettings) => void }) {
  const { t } = useI18n();
  const [draft, setDraft] = useState<AiSettings>(loadSettings);
  const [saved, setSaved] = useState(false);
  const problem = settingsProblem(draft);
  const update = (patch: Partial<AiSettings>) => {
    setDraft({ ...draft, ...patch });
    setSaved(false);
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (problem) return;
    saveSettings(draft);
    setSaved(true);
    onSaved(draft);
  };
  return (
    <form onSubmit={submit}>
      <p className="muted small">{t("assistant.ai.privacy")}</p>
      <div className="form-grid">
        <Field label={t("assistant.ai.provider")}>
          <select
            value={draft.provider}
            onChange={(e) => {
              const provider = e.target.value as Provider;
              update({ provider, model: DEFAULT_MODELS[provider] });
            }}
          >
            {(["ollama", "anthropic", "openai"] as const).map((p) => (
              <option key={p} value={p}>
                {t(`assistant.ai.provider.${p}` as MessageKey)}
              </option>
            ))}
          </select>
        </Field>
        <Field label={t("assistant.ai.model")}>
          <input maxLength={100} value={draft.model} onChange={(e) => update({ model: e.target.value.trim() })} />
        </Field>
        {draft.provider === "ollama" ? (
          <Field label={t("assistant.ai.url")}>
            <input maxLength={60} value={draft.ollamaUrl} onChange={(e) => update({ ollamaUrl: e.target.value.trim() })} />
          </Field>
        ) : (
          <Field label={t("assistant.ai.key")}>
            <input type="password" autoComplete="off" spellCheck={false} maxLength={300} value={draft.apiKey} onChange={(e) => update({ apiKey: e.target.value })} />
          </Field>
        )}
      </div>
      <p className="muted small">{t(`assistant.ai.hint.${draft.provider}` as MessageKey)}</p>
      <label className="check">
        <input type="checkbox" checked={draft.remember} onChange={(e) => update({ remember: e.target.checked })} /> {t("assistant.ai.remember")}
      </label>
      {problem && <p className="muted small">{t(problem)}</p>}
      <Button type="submit" variant="primary" disabled={problem !== null}>
        {saved ? t("assistant.ai.saved") : t("assistant.ai.save")}
      </Button>
    </form>
  );
}

/** Chat com a IA escolhida pela pessoa, usando as ferramentas do JobDragonQuest com a sessão do painel. */
export function AssistantChat({ seed }: { seed?: { text: string; n: number } }) {
  const { t } = useI18n();
  const [settings, setSettings] = useState<AiSettings>(loadSettings);
  const [history, setHistory] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [limited, setLimited] = useState(false);
  const [pending, setPending] = useState<PendingConfirm | null>(null);
  const [configured, setConfigured] = useState(isConfigured);
  const ready = configured && settingsProblem(settings) === null;
  const historyRef = useRef(history);
  historyRef.current = history;

  const send = async (text: string) => {
    const message = text.trim().slice(0, MAX_INPUT);
    if (!message || busy || !ready) return;
    setBusy(true);
    setError(null);
    setLimited(false);
    setInput("");
    const start: ChatMessage[] = [...historyRef.current, { role: "user", text: message }];
    setHistory(start);
    try {
      const result = await runAgent({
        history: start,
        send: (h, tools) => chat(settings, SYSTEM_PROMPT, h, tools),
        confirm: (tool, args) => new Promise<boolean>((resolve) => setPending({ tool, args, resolve })),
        onUpdate: setHistory,
      });
      setHistory(result.history);
      setLimited(result.limited);
    } catch (e) {
      if (e instanceof AiError && e.status === 0) {
        setError(t(settings.provider === "ollama" ? "assistant.chat.unreachableOllama" : "assistant.chat.unreachable"));
      } else {
        setError(t("assistant.chat.error", { detail: e instanceof Error ? e.message : "?" }));
      }
    } finally {
      setBusy(false);
      setPending(null);
    }
  };

  useEffect(() => {
    if (seed && seed.n > 0) void send(seed.text);
    // Só quando a página manda um pedido novo.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seed?.n]);

  const answer = (allowed: boolean) => {
    pending?.resolve(allowed);
    setPending(null);
  };

  return (
    <>
      <Card title={t("assistant.chat.title")}>
        <p className="muted small">{t("assistant.chat.intro")}</p>
        {!ready && <Notice tone="info">{t("assistant.chat.setup")}</Notice>}
        <div className="chat-log" role="log" aria-live="polite">
          {history.map((message, index) => {
            if (message.role === "user") {
              return (
                <p key={index} className="chat-user prewrap">
                  <strong>{t("assistant.chat.you")}:</strong> {message.text}
                </p>
              );
            }
            if (message.role === "tool") {
              return (
                <p key={index} className="muted small">
                  {t(message.isError ? "assistant.chat.toolFailed" : "assistant.chat.usedTool", { tool: message.name.replace("jobdragonquest_", "") })}
                </p>
              );
            }
            return message.text ? (
              <p key={index} className="chat-ai prewrap">
                <strong>{t("assistant.chat.ai")}:</strong> {message.text}
              </p>
            ) : null;
          })}
          {busy && !pending && <p className="muted small">{t("assistant.chat.thinking")}</p>}
        </div>
        {pending && (
          <div className="chat-confirm" role="alertdialog" aria-label={t("assistant.chat.confirmTitle")}>
            <p>
              <strong>{t("assistant.chat.confirmTitle")}</strong> {pending.tool.title}
            </p>
            <pre className="prewrap small">{JSON.stringify(pending.args, null, 2).slice(0, 4000)}</pre>
            <Button variant="primary" onClick={() => answer(true)}>
              {t("assistant.chat.allow")}
            </Button>{" "}
            <Button onClick={() => answer(false)}>{t("assistant.chat.deny")}</Button>
          </div>
        )}
        {limited && <Notice tone="info">{t("assistant.chat.limit")}</Notice>}
        {error && (
          <Notice tone="error" onClose={() => setError(null)}>
            {error}
          </Notice>
        )}
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void send(input);
          }}
        >
          <Field label={t("assistant.chat.message")} wide>
            <textarea rows={3} maxLength={MAX_INPUT} value={input} disabled={!ready} placeholder={t("assistant.chat.placeholder")} onChange={(e) => setInput(e.target.value)} />
          </Field>
          <Button type="submit" variant="primary" disabled={!ready || busy || input.trim() === ""}>
            {t("assistant.chat.send")}
          </Button>{" "}
          <Button type="button" disabled={busy || history.length === 0} onClick={() => setHistory([])}>
            {t("assistant.chat.clear")}
          </Button>
        </form>
      </Card>
      <Card title={t("assistant.ai.title")}>
        <AiSettingsForm
          onSaved={(saved) => {
            setSettings(saved);
            setConfigured(true);
          }}
        />
      </Card>
    </>
  );
}
