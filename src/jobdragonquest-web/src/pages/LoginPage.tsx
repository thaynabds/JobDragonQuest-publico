import { useState, type FormEvent } from "react";

import { api } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { LanguageSelect } from "../components/LanguageSelect";
import { Button, Field, Notice } from "../components/ui";
import { PasswordInput } from "../components/PasswordInput";
import { useI18n } from "../i18n/I18nContext";

export function LoginPage() {
  const { t, locale, errorMessage } = useI18n();
  const { login, register } = useAuth();
  const [mode, setMode] = useState<"login" | "register" | "recover">("login");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [remember, setRemember] = useState(true);
  const [code, setCode] = useState("");
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === "recover") {
        await api.recoverAccount({ email: email.trim(), code: code.trim(), newPassword: password });
        setMode("login");
        setCode("");
        setPassword("");
        setInfo(t("auth.recovered"));
      } else if (mode === "login") {
        await login(email.trim(), password, remember);
      } else {
        await register(displayName.trim(), email.trim(), password, locale);
      }
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={(event) => void submit(event)} noValidate>
        <div className="brand brand-large">
          <img className="brand-logo" src="/logo.png" alt="" width="44" height="44" />
          <div>
            <strong>{t("app.name")}</strong>
            <span className="brand-tagline">{t("app.tagline")}</span>
          </div>
        </div>
        <h1>{mode === "recover" ? t("auth.recoverTitle") : mode === "login" ? t("auth.loginTitle") : t("auth.registerTitle")}</h1>
        {info && <Notice tone="success">{info}</Notice>}
        {mode === "recover" && <p className="muted small">{t("auth.recoverHint")}</p>}
        {error && <Notice tone="error">{error}</Notice>}
        {mode === "register" && (
          <Field label={t("auth.displayName")}>
            <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} required maxLength={120} autoComplete="name" />
          </Field>
        )}
        <Field label={t("auth.email")}>
          <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required maxLength={254} autoComplete="email" />
        </Field>
        {mode === "recover" && (
          <Field label={t("auth.recoveryCode")}>
            <input value={code} onChange={(event) => setCode(event.target.value)} required maxLength={64} autoComplete="one-time-code" />
          </Field>
        )}
        <Field label={mode === "recover" ? t("account.newPassword") : t("auth.password")} hint={mode !== "login" ? t("auth.passwordHint") : undefined}>
          <PasswordInput
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            minLength={mode === "login" ? 1 : 12}
            maxLength={128}
            autoComplete={mode === "login" ? "current-password" : "new-password"}
          />
        </Field>
        {mode === "login" && (
          <label className="checkbox">
            <input type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)} /> {t("auth.remember")}
          </label>
        )}
        <Button type="submit" variant="primary" disabled={busy}>
          {mode === "recover" ? t("auth.recoverSubmit") : mode === "login" ? t("auth.login") : t("auth.register")}
        </Button>
        {mode === "login" && (
          <button type="button" className="link-button" onClick={() => { setError(null); setInfo(null); setMode("recover"); }}>
            {t("auth.forgot")}
          </button>
        )}
        <button type="button" className="link-button" onClick={() => setMode(mode === "login" ? "register" : "login")}>
          {mode === "login" ? t("auth.toRegister") : t("auth.toLogin")}
        </button>
        <LanguageSelect className="lang lang-inline" />
      </form>
    </div>
  );
}
