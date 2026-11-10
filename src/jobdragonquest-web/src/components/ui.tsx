import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Link } from "react-router";

import { useI18n } from "../i18n/I18nContext";
import { safeUrl } from "../utils/safe";

type Variant = "primary" | "secondary" | "danger" | "ghost";

export function Button({ variant = "secondary", className, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return <button type="button" className={`btn btn-${variant}${className ? ` ${className}` : ""}`} {...props} />;
}

export function Card({ title, actions, children, className }: { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`card${className ? ` ${className}` : ""}`}>
      {(title || actions) && (
        <header className="card-header">
          {title && <h2>{title}</h2>}
          {actions && <div className="card-actions">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  );
}

/** Botão "i": ao passar o cursor ou focar, abre um balão explicando o campo. */
export function InfoTip({ text }: { text: string }) {
  return (
    <span className="info-tip">
      <button type="button" aria-label={text}>
        i
      </button>
      <span role="tooltip">{text}</span>
    </span>
  );
}

export function Field({ label, hint, info, children, wide }: { label: string; hint?: string; info?: string; children: ReactNode; wide?: boolean }) {
  const field = (
    <label className={`field${wide && !info ? " field-wide" : ""}`}>
      <span className="field-label">{label}</span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  );
  if (!info) return field;
  // Fora do <label>: o texto de ajuda não entra no nome acessível do campo.
  return (
    <div className={`field-with-info${wide ? " field-wide" : ""}`}>
      {field}
      <InfoTip text={info} />
    </div>
  );
}

export function Badge({ tone = "neutral", children }: { tone?: "neutral" | "info" | "good" | "warn" | "bad"; children: ReactNode }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

export function Notice({ tone, children, onClose }: { tone: "error" | "success" | "info"; children: ReactNode; onClose?: () => void }) {
  return (
    <div className={`notice notice-${tone}`} role={tone === "error" ? "alert" : "status"}>
      <span>{children}</span>
      {onClose && (
        <button type="button" className="notice-close" aria-label="×" onClick={onClose}>
          ×
        </button>
      )}
    </div>
  );
}

export function Loading() {
  const { t } = useI18n();
  return (
    <p className="muted" role="status">
      {t("common.loading")}
    </p>
  );
}

export function Empty({ children }: { children?: ReactNode }) {
  const { t } = useI18n();
  return <p className="empty">{children ?? t("common.empty")}</p>;
}

/** Link externo seguro: só http(s), abre em outra aba sem dar acesso à janela de origem. */
export function ExternalLink({ href, children }: { href: string | null | undefined; children: ReactNode }) {
  const url = safeUrl(href);
  if (!url) {
    return null;
  }
  return (
    <a href={url} target="_blank" rel="noopener noreferrer" className="external">
      {children}
    </a>
  );
}

export function Stat({
  label,
  value,
  tone,
  to,
  info,
}: {
  label: string;
  value: number | string;
  tone?: "warn" | "good" | "bad";
  to?: string;
  info?: string;
}) {
  const className = `stat${tone ? ` stat-${tone}` : ""}`;
  const content = (
    <>
      <span className="stat-value">{value}</span>
      <span className="stat-label">{label}</span>
    </>
  );
  return (
    <div className="stat-wrap">
      {to ? (
        <Link className={`${className} stat-link`} to={to}>
          {content}
        </Link>
      ) : (
        <div className={className}>{content}</div>
      )}
      {info && <InfoTip text={info} />}
    </div>
  );
}
