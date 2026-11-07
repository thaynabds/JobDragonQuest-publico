import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useNavigate } from "react-router";

import { api } from "../api/endpoints";
import type { NotificationItem } from "../api/types";
import { useI18n } from "../i18n/I18nContext";
import { playChime } from "./sound";
import { notificationLink, notificationText, notificationTitle } from "./text";

type Permission = NotificationPermission | "unsupported";

interface Notifications {
  toasts: NotificationItem[];
  dismiss: (id: string) => void;
  unread: number;
  refreshUnread: () => void;
  hideDetails: boolean;
  setHideDetails: (value: boolean) => void;
  permission: Permission;
  requestPermission: () => Promise<void>;
  /** Consulta agora, sem esperar o intervalo (ex.: depois de pedir um aviso de teste). */
  pollNow: () => void;
}

const NotificationsContext = createContext<Notifications | null>(null);
export const POLL_MS = 15_000;
const MAX_TOASTS = 5;

function currentPermission(): Permission {
  return typeof window !== "undefined" && "Notification" in window ? Notification.permission : "unsupported";
}

/**
 * Consulta os avisos pendentes de entrega a cada poucos segundos (e logo ao abrir), mostra pop-up, notificação do
 * sistema e som conforme os canais que a API decidiu, e marca como entregues. Avisos só existem com o painel aberto:
 * o navegador não recebe nada com a aba fechada.
 */
export function NotificationsProvider({ children, pollMs = POLL_MS }: { children: ReactNode; pollMs?: number }) {
  const { t, label, formatDate, locale } = useI18n();
  const navigate = useNavigate();
  const [toasts, setToasts] = useState<NotificationItem[]>([]);
  const [unread, setUnread] = useState(0);
  const [hideDetails, setHideDetails] = useState(false);
  const [permission, setPermission] = useState<Permission>(currentPermission);
  const seen = useRef(new Set<string>());
  const busy = useRef(false);
  const hideRef = useRef(hideDetails);
  hideRef.current = hideDetails;

  const refreshUnread = useCallback(() => {
    api
      .notifications({ size: 1 })
      .then((response) => setUnread(response.data.unreadCount))
      .catch(() => undefined);
  }, []);

  const showSystem = useCallback(
    (n: NotificationItem) => {
      if (currentPermission() !== "granted") {
        return;
      }
      try {
        const body = hideRef.current ? t("notif.hidden") : notificationText(n, t, label, formatDate);
        const system = new Notification(hideRef.current ? "JobFly" : `JobFly · ${notificationTitle(n, t)}`, {
          body,
          tag: n.id,
          lang: locale,
          icon: "/logo-192.png",
        });
        system.onclick = () => {
          window.focus();
          const link = notificationLink(n);
          if (link) {
            navigate(link);
          }
          system.close();
        };
      } catch {
        // Alguns navegadores só permitem notificação por service worker: o pop-up do painel continua valendo.
      }
    },
    [t, label, formatDate, locale, navigate],
  );

  const poll = useCallback(async () => {
    if (busy.current) {
      return;
    }
    busy.current = true;
    try {
      const due = (await api.notificationsDue()).data.filter((n) => !seen.current.has(n.id));
      if (due.length === 0) {
        return;
      }
      due.forEach((n) => seen.current.add(n.id));
      const popups = due.filter((n) => n.popup);
      if (popups.length > 0) {
        setToasts((current) => [...popups.reverse(), ...current].slice(0, MAX_TOASTS));
      }
      due.filter((n) => n.system).forEach(showSystem);
      if (due.some((n) => n.sound)) {
        playChime();
      }
      await api.markDelivered(due.map((n) => n.id));
      refreshUnread();
    } catch {
      // Sem conexão agora: tenta de novo no próximo intervalo.
    } finally {
      busy.current = false;
    }
  }, [showSystem, refreshUnread]);

  useEffect(() => {
    api
      .notificationPreferences()
      .then((response) => setHideDetails(response.data.settings.hideDetails))
      .catch(() => undefined);
    refreshUnread();
    void poll();
    const timer = window.setInterval(() => void poll(), pollMs);
    return () => window.clearInterval(timer);
  }, [poll, pollMs, refreshUnread]);

  const dismiss = useCallback((id: string) => setToasts((current) => current.filter((n) => n.id !== id)), []);

  const requestPermission = useCallback(async () => {
    if (!("Notification" in window)) {
      setPermission("unsupported");
      return;
    }
    setPermission(await Notification.requestPermission());
  }, []);

  const pollNow = useCallback(() => void poll(), [poll]);

  const value = useMemo<Notifications>(
    () => ({ toasts, dismiss, unread, refreshUnread, hideDetails, setHideDetails, permission, requestPermission, pollNow }),
    [toasts, dismiss, unread, refreshUnread, hideDetails, permission, requestPermission, pollNow],
  );
  return <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>;
}

export function useNotifications(): Notifications {
  const context = useContext(NotificationsContext);
  if (!context) {
    throw new Error("useNotifications fora do NotificationsProvider");
  }
  return context;
}
