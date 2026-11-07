import { useEffect } from "react";
import { useNavigate } from "react-router";

import { api } from "../api/endpoints";
import type { NotificationItem } from "../api/types";
import { useI18n } from "../i18n/I18nContext";
import { useNotifications } from "./NotificationsContext";
import { notificationLink, notificationText, notificationTitle } from "./text";

export const AUTO_DISMISS_MS = 12_000;

function Toast({ item, autoDismissMs }: { item: NotificationItem; autoDismissMs: number }) {
  const { t, label, formatDate } = useI18n();
  const { dismiss, refreshUnread } = useNotifications();
  const navigate = useNavigate();
  const high = item.priority === "HIGH";
  const link = notificationLink(item);

  // Prioridade alta fica até a pessoa fechar; as outras somem sozinhas (continuam no sino).
  useEffect(() => {
    if (high) {
      return;
    }
    const timer = setTimeout(() => dismiss(item.id), autoDismissMs);
    return () => clearTimeout(timer);
  }, [high, item.id, dismiss, autoDismissMs]);

  const read = () => {
    dismiss(item.id);
    api.markRead(item.id).then(refreshUnread).catch(() => undefined);
  };
  const snooze = () => {
    dismiss(item.id);
    api.snooze(item.id, 60).then(refreshUnread).catch(() => undefined);
  };

  return (
    <div className={`toast toast-${item.priority.toLowerCase()}`} role={high ? "alert" : "status"} aria-live={high ? "assertive" : "polite"}>
      <div className="toast-body">
        <strong>{notificationTitle(item, t)}</strong>
        <span>{notificationText(item, t, label, formatDate)}</span>
      </div>
      <div className="toast-actions">
        {link && (
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => {
              read();
              navigate(link);
            }}
          >
            {t("common.open")}
          </button>
        )}
        <button type="button" className="btn" onClick={snooze}>
          {t("notif.snooze")}
        </button>
        <button type="button" className="btn" onClick={read}>
          {t("notif.markRead")}
        </button>
        <button type="button" className="btn btn-ghost" aria-label={t("notif.close")} onClick={() => dismiss(item.id)}>
          ×
        </button>
      </div>
    </div>
  );
}

/** Pilha de pop-ups no canto inferior esquerdo (o direito é do botão de acessibilidade). */
export function Toasts({ autoDismissMs = AUTO_DISMISS_MS }: { autoDismissMs?: number }) {
  const { toasts } = useNotifications();
  if (toasts.length === 0) {
    return null;
  }
  return (
    <div className="toasts" aria-label="JobDragonQuest">
      {toasts.map((item) => (
        <Toast key={item.id} item={item} autoDismissMs={autoDismissMs} />
      ))}
    </div>
  );
}
