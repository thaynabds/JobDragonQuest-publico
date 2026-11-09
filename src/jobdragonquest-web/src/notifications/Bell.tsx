import { useEffect, useRef, useState } from "react";
import { Link } from "react-router";

import { api } from "../api/endpoints";
import type { NotificationItem } from "../api/types";
import { useI18n } from "../i18n/I18nContext";
import { useNotifications } from "./NotificationsContext";
import { notificationLink, notificationText, notificationTitle } from "./text";

/** Sino com contador e histórico dos avisos. */
export function Bell() {
  const { t, label, formatDate, errorMessage } = useI18n();
  const { unread, refreshUnread, permission, requestPermission, pollNow } = useNotifications();
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<NotificationItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const panel = useRef<HTMLDivElement>(null);

  const load = () => {
    api
      .notifications({ size: 20 })
      .then((response) => {
        setItems(response.data.items);
        setError(null);
      })
      .catch((err: unknown) => setError(errorMessage(err)));
  };

  useEffect(() => {
    if (!open) {
      return;
    }
    load();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // Com o sino aberto, a lista acompanha leituras e adiamentos feitos pelo pop-up (o contador muda).
  useEffect(() => {
    if (open) {
      load();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [unread]);

  const act = (promise: Promise<unknown>) =>
    promise
      .then(() => {
        load();
        refreshUnread();
      })
      .catch((err: unknown) => setError(errorMessage(err)));

  return (
    <div className="bell">
      <button
        type="button"
        className="btn btn-ghost bell-button"
        aria-expanded={open}
        aria-controls="bell-panel"
        aria-label={unread > 0 ? t("notif.bellUnread", { count: unread }) : t("notif.bell")}
        onClick={() => setOpen(!open)}
      >
        <span aria-hidden="true">🔔</span>
        {unread > 0 && (
          <span className="bell-count" aria-hidden="true">
            {unread > 99 ? "99+" : unread}
          </span>
        )}
      </button>
      {open && (
        <div ref={panel} id="bell-panel" className="bell-panel" role="region" aria-label={t("notif.bell")}>
          <header className="bell-header">
            <strong>{t("notif.bell")}</strong>
            <button type="button" className="btn btn-ghost" onClick={() => void act(api.markAllRead())} disabled={unread === 0}>
              {t("notif.markAllRead")}
            </button>
          </header>
          {permission === "default" && (
            <button type="button" className="btn btn-primary" onClick={() => void requestPermission()}>
              {t("notif.enableSystem")}
            </button>
          )}
          {permission === "denied" && <p className="muted">{t("notif.systemDenied")}</p>}
          {error && <p className="notice notice-error">{error}</p>}
          {items && items.length === 0 && <p className="muted">{t("notif.empty")}</p>}
          <ul className="bell-list">
            {items?.map((item) => {
              const link = notificationLink(item);
              return (
                <li key={item.id} className={item.readAt ? "bell-item read" : "bell-item"}>
                  <div>
                    <strong>{notificationTitle(item, t)}</strong>
                    <span>{notificationText(item, t, label, formatDate)}</span>
                    <small className="muted">{formatDate(item.createdAt, true)}</small>
                  </div>
                  <div className="bell-item-actions">
                    {link && (
                      <Link
                        to={link}
                        className="btn"
                        onClick={() => {
                          setOpen(false);
                          void act(api.markRead(item.id));
                        }}
                      >
                        {t("common.open")}
                      </Link>
                    )}
                    {!item.readAt && (
                      <button type="button" className="btn" onClick={() => void act(api.markRead(item.id))}>
                        {t("notif.markRead")}
                      </button>
                    )}
                    <button type="button" className="btn" onClick={() => void act(api.snooze(item.id, 60))}>
                      {t("notif.snooze")}
                    </button>
                    <button type="button" className="btn" onClick={() => void act(api.dismissNotification(item.id))}>
                      {t("notif.delete")}
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
          <footer className="bell-footer">
            <button
              type="button"
              className="btn"
              disabled={!items || items.length === 0}
              onClick={() => {
                if (window.confirm(t("notif.confirmClear"))) void act(api.dismissAllNotifications());
              }}
            >
              {t("notif.clearAll")}
            </button>
            <button
              type="button"
              className="btn"
              onClick={() =>
                void act(api.testNotification()).then(() => {
                  pollNow();
                })
              }
            >
              {t("notif.test")}
            </button>
            <Link to="/configuracoes" className="btn btn-ghost" onClick={() => setOpen(false)}>
              {t("notif.settingsLink")}
            </Link>
          </footer>
        </div>
      )}
    </div>
  );
}
