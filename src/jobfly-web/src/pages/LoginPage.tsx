import { useState, type FormEvent } from "react";

import { useAuth } from "../auth/AuthContext";
import { Button, Field, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";

export function LoginPage() {
  const { t, locale, setLocale, errorMessage } = useI18n();
  const { login, register } = useAuth();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === "login") {
        await login(email.trim(), password);
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
        <h1>{mode === "login" ? t("auth.loginTitle") : t("auth.registerTitle")}</h1>
        {error && <Notice tone="error">{error}</Notice>}
        {mode === "register" && (
          <Field label={t("auth.displayName")}>
            <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} required maxLength={120} autoComplete="name" />
          </Field>
        )}
        <Field label={t("auth.email")}>
          <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required maxLength={254} autoComplete="email" />
        </Field>
        <Field label={t("auth.password")} hint={mode === "register" ? t("auth.passwordHint") : undefined}>
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            minLength={mode === "register" ? 12 : 1}
            maxLength={128}
            autoComplete={mode === "login" ? "current-password" : "new-password"}
          />
        </Field>
        <Button type="submit" variant="primary" disabled={busy}>
          {mode === "login" ? t("auth.login") : t("auth.register")}
        </Button>
        <button type="button" className="link-button" onClick={() => setMode(mode === "login" ? "register" : "login")}>
          {mode === "login" ? t("auth.toRegister") : t("auth.toLogin")}
        </button>
        <label className="lang lang-inline">
          <span>{t("lang.label")}</span>
          <select value={locale} onChange={(event) => setLocale(event.target.value === "en" ? "en" : "pt-BR")}>
            <option value="pt-BR">Português</option>
            <option value="en">English</option>
          </select>
        </label>
      </form>
    </div>
  );
}
