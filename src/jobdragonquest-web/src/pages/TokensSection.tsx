import { useState, type FormEvent } from "react";

import { api } from "../api/endpoints";
import type { CreatedToken } from "../api/types";
import { Badge, Button, Card, Empty, Field, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";

const DAY_OPTIONS = [30, 90, 180, 365] as const;

/** Tokens de acesso pessoal. O valor aparece uma única vez, só em memória, até a pessoa fechar o aviso. */
export function TokensSection() {
  const { t, formatDate, errorMessage } = useI18n();
  const tokens = useAsync(() => api.tokens().then((response) => response.data), []);
  const [name, setName] = useState("");
  const [days, setDays] = useState<number>(90);
  const [created, setCreated] = useState<CreatedToken | null>(null);
  const [copied, setCopied] = useState(false);
  const [message, setMessage] = useState<{ tone: "error" | "success"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      setCreated((await api.createToken(days === 0 ? { name: name.trim(), neverExpires: true } : { name: name.trim(), days })).data);
      setCopied(false);
      setName("");
      tokens.reload();
    } catch (error) {
      setMessage({ tone: "error", text: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  const revoke = async (tokenId: string) => {
    if (!window.confirm(t("tokens.confirmRevoke"))) {
      return;
    }
    try {
      await api.revokeToken(tokenId);
      setMessage({ tone: "success", text: t("tokens.revoked") });
      tokens.reload();
    } catch (error) {
      setMessage({ tone: "error", text: errorMessage(error) });
    }
  };

  const copy = async () => {
    if (!created) {
      return;
    }
    try {
      await navigator.clipboard.writeText(created.value);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  return (
    <Card title={t("tokens.title")}>
      <p className="muted">{t("tokens.intro")}</p>
      {message && (
        <Notice tone={message.tone} onClose={() => setMessage(null)}>
          {message.text}
        </Notice>
      )}
      {created && (
        <Notice tone="success" onClose={() => setCreated(null)}>
          {t("tokens.created")}{" "}
          <code className="token-value" data-testid="token-value">
            {created.value}
          </code>{" "}
          <Button onClick={() => void copy()}>{copied ? t("tokens.copied") : t("tokens.copy")}</Button>
        </Notice>
      )}
      <form className="form-row" onSubmit={(event) => void submit(event)}>
        <Field label={t("tokens.name")} info={t("help.tokens.name")}>
          <input value={name} onChange={(event) => setName(event.target.value)} maxLength={80} required placeholder={t("tokens.namePlaceholder")} />
        </Field>
        <Field label={t("tokens.days")} info={t("help.tokens.days")}>
          <select value={days} onChange={(event) => setDays(Number(event.target.value))}>
            {DAY_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {t("tokens.daysOption", { days: option })}
              </option>
            ))}
            <option value={0}>{t("tokens.never")}</option>
          </select>
        </Field>
        <Button type="submit" variant="primary" disabled={busy || !name.trim()}>
          {t("tokens.create")}
        </Button>
      </form>
      {tokens.error ? <Notice tone="error">{errorMessage(tokens.error)}</Notice> : null}
      {tokens.loading && !tokens.data ? <Loading /> : null}
      {tokens.data && tokens.data.length === 0 && <Empty>{t("tokens.empty")}</Empty>}
      {tokens.data && tokens.data.length > 0 && (
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th>{t("tokens.name")}</th>
                <th>{t("tokens.prefix")}</th>
                <th>{t("tokens.createdAt")}</th>
                <th>{t("tokens.expiresAt")}</th>
                <th>{t("tokens.lastUsed")}</th>
                <th>{t("tokens.status")}</th>
                <th>{t("common.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {tokens.data.map((token) => (
                <tr key={token.id}>
                  <td>{token.name}</td>
                  <td>
                    <code>{token.prefix}</code>
                  </td>
                  <td>{formatDate(token.createdAt)}</td>
                  <td>{token.expiresAt ? formatDate(token.expiresAt) : t("tokens.never")}</td>
                  <td>{formatDate(token.lastUsedAt, true)}</td>
                  <td>{token.active ? <Badge tone="good">{t("tokens.active")}</Badge> : <Badge>{t("tokens.inactive")}</Badge>}</td>
                  <td>
                    {token.active && (
                      <Button variant="danger" onClick={() => void revoke(token.id)}>
                        {t("tokens.revoke")}
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}
