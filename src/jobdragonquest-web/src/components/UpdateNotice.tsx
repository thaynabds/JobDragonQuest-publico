import { useEffect, useState } from "react";

import { api } from "../api/endpoints";
import type { SystemVersion } from "../api/types";
import { useI18n } from "../i18n/I18nContext";
import { ExternalLink } from "./ui";

const KEY = "jdq-update-dismissed";
const SIX_HOURS = 6 * 60 * 60 * 1000;
const PANEL_VERSION = __APP_VERSION__;

/** Compara versões número a número. Versão sem números (ambiente de desenvolvimento) nunca é mais nova. */
export function isNewer(candidate: string, base: string): boolean {
  const parse = (v: string) => v.split(".").map(Number);
  const a = parse(candidate);
  const b = parse(base);
  if ([...a, ...b].some(Number.isNaN)) return false;
  for (let i = 0; i < 3; i++) {
    const difference = (a[i] ?? 0) - (b[i] ?? 0);
    if (difference !== 0) return difference > 0;
  }
  return false;
}

function dismissed(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

/** Aviso dentro do painel: há versão nova publicada, ou o servidor foi atualizado e esta aba ainda está na antiga. */
export function UpdateNotice() {
  const { t } = useI18n();
  const [info, setInfo] = useState<SystemVersion | null>(null);
  const [closed, setClosed] = useState<string | null>(dismissed);

  useEffect(() => {
    const load = () =>
      api
        .systemVersion()
        .then((response) => setInfo(response.data))
        .catch(() => undefined);
    void load();
    const timer = window.setInterval(() => void load(), SIX_HOURS);
    return () => window.clearInterval(timer);
  }, []);

  if (!info) return null;
  // Só a API mais nova que o painel pede recarregar; durante um deploy, o painel pode chegar antes da API.
  const stale = info.current !== null && isNewer(info.current, PANEL_VERSION);
  const version = stale ? info.current : info.updateAvailable ? info.latest : null;
  if (!version || closed === version) return null;
  const close = () => {
    setClosed(version);
    try {
      localStorage.setItem(KEY, version);
    } catch {
      // Sem armazenamento (janela privada): o aviso só some nesta tela.
    }
  };
  return (
    <div className="notice notice-info update-notice" role="status">
      <span>
        {stale ? t("update.reload", { version }) : t("update.available", { version })}{" "}
        {!stale && info.releaseUrl && <ExternalLink href={info.releaseUrl}>{t("update.notes")}</ExternalLink>}
      </span>
      {stale && (
        <button type="button" className="btn" onClick={() => window.location.reload()}>
          {t("update.reloadButton")}
        </button>
      )}
      <button type="button" className="btn btn-ghost" onClick={close}>
        {t("update.dismiss")}
      </button>
    </div>
  );
}
