import { useState, type FormEvent } from "react";

import { api } from "../api/endpoints";
import { setAccessToken } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { PasswordInput } from "../components/PasswordInput";
import { Button, Card, Field, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";

/** Dados da conta: nome, e-mail de login e senha. E-mail e senha pedem a senha atual. */
export function AccountSection() {
  const { t, errorMessage } = useI18n();
  const { user, reloadUser, logout } = useAuth();
  const [deletePassword, setDeletePassword] = useState("");
  const [name, setName] = useState(user?.displayName ?? "");
  const [email, setEmail] = useState(user?.email ?? "");
  const [emailPassword, setEmailPassword] = useState("");
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [recoveryPassword, setRecoveryPassword] = useState("");
  const [codes, setCodes] = useState<string[] | null>(null);
  const status = useAsync(() => api.recoveryStatus().then((r) => r.data), []);
  const [message, setMessage] = useState<{ tone: "success" | "error"; text: string } | null>(null);

  const run = (event: FormEvent, action: () => Promise<void>) => {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    action()
      .then(() => setMessage({ tone: "success", text: t("common.saved") }))
      .catch((err: unknown) => setMessage({ tone: "error", text: errorMessage(err) }))
      .finally(() => setBusy(false));
  };

  return (
    <Card title={t("account.title")}>
      {message && (
        <Notice tone={message.tone} onClose={() => setMessage(null)}>
          {message.text}
        </Notice>
      )}
      <form
        className="form-row"
        aria-label={t("account.name")}
        onSubmit={(e) =>
          run(e, async () => {
            await api.updateName(name.trim());
            await reloadUser();
          })
        }
      >
        <Field label={t("account.name")}>
          <input value={name} required maxLength={120} autoComplete="name" onChange={(e) => setName(e.target.value)} />
        </Field>
        <Button type="submit" disabled={busy || !name.trim()}>
          {t("account.saveName")}
        </Button>
      </form>
      <form
        className="form-row"
        aria-label={t("account.email")}
        onSubmit={(e) =>
          run(e, async () => {
            await api.updateEmail(email.trim(), emailPassword);
            setEmailPassword("");
            await reloadUser();
          })
        }
      >
        <Field label={t("account.email")}>
          <input type="email" value={email} required maxLength={254} autoComplete="email" onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label={t("account.currentPassword")}>
          <PasswordInput value={emailPassword} required maxLength={128} autoComplete="current-password" onChange={(e) => setEmailPassword(e.target.value)} />
        </Field>
        <Button type="submit" disabled={busy || !emailPassword}>
          {t("account.saveEmail")}
        </Button>
      </form>
      <form
        className="form-row"
        aria-label={t("account.newPassword")}
        onSubmit={(e) => {
          if (next !== confirm) {
            e.preventDefault();
            setMessage({ tone: "error", text: t("account.passwordsDiffer") });
            return;
          }
          run(e, async () => {
            const { data } = await api.updatePassword(current, next);
            setAccessToken(data.accessToken);
            setCurrent("");
            setNext("");
            setConfirm("");
          });
        }}
      >
        <Field label={t("account.currentPassword")}>
          <PasswordInput value={current} required maxLength={128} autoComplete="current-password" onChange={(e) => setCurrent(e.target.value)} />
        </Field>
        <Field label={t("account.newPassword")} hint={t("auth.passwordHint")}>
          <PasswordInput value={next} required minLength={12} maxLength={128} autoComplete="new-password" onChange={(e) => setNext(e.target.value)} />
        </Field>
        <Field label={t("account.confirmPassword")}>
          <PasswordInput value={confirm} required minLength={12} maxLength={128} autoComplete="new-password" onChange={(e) => setConfirm(e.target.value)} />
        </Field>
        <Button type="submit" disabled={busy}>
          {t("account.savePassword")}
        </Button>
      </form>
      <p className="muted small">{t("account.passwordNote")}</p>
      <h3>{t("account.recoveryTitle")}</h3>
      <p className="muted small">{t("account.recoveryIntro", { count: status.data?.remaining ?? 0 })}</p>
      <form
        className="form-row"
        aria-label={t("account.recoveryTitle")}
        onSubmit={(e) =>
          run(e, async () => {
            const { data } = await api.newRecoveryCodes(recoveryPassword);
            setCodes(data.codes);
            setRecoveryPassword("");
            status.reload();
          })
        }
      >
        <Field label={t("account.currentPassword")}>
          <PasswordInput value={recoveryPassword} required maxLength={128} autoComplete="current-password" onChange={(e) => setRecoveryPassword(e.target.value)} />
        </Field>
        <Button type="submit" disabled={busy || !recoveryPassword}>
          {t("account.recoveryGenerate")}
        </Button>
      </form>
      <form onSubmit={(event) => run(event, async () => { await api.deleteAccount(deletePassword); await logout(); })}>
        <h3>{t("account.deleteTitle")}</h3>
        <p className="muted small">{t("account.deleteWarning")}</p>
        <Field label={t("auth.password")}><PasswordInput value={deletePassword} required maxLength={128} autoComplete="current-password" onChange={(e) => setDeletePassword(e.target.value)} /></Field>
        <Button type="submit" disabled={busy || !deletePassword}>{t("account.delete")}</Button>
      </form>
      {codes && (
        <Notice tone="info" onClose={() => setCodes(null)}>
          {t("account.recoveryShown")} <code className="prewrap">{codes.join("\n")}</code>
        </Notice>
      )}
    </Card>
  );
}
