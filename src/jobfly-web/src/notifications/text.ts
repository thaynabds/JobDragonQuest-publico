import type { NotificationItem } from "../api/types";
import type { MessageKey } from "../i18n/messages";

type T = (key: MessageKey, values?: Record<string, string | number>) => string;
type Label = (prefix: string, value: string | null | undefined) => string;
type FormatDate = (iso: string | null | undefined, withTime?: boolean) => string;

/** Quem é a candidatura: "Empresa (Cargo)", só um dos dois ou o genérico. Texto puro: o React escapa ao exibir. */
export function who(n: Pick<NotificationItem, "company" | "title">, t: T): string {
  if (n.company && n.title) {
    return `${n.company} (${n.title})`;
  }
  return n.company || n.title || t("notif.application");
}

/** Título curto (tipo do aviso) no idioma da pessoa. */
export function notificationTitle(n: NotificationItem, t: T): string {
  return t(`notif.type.${n.type}` as MessageKey);
}

/** Frase do aviso montada no idioma da pessoa pelos campos estruturados; cai na mensagem da API se faltar algo. */
export function notificationText(n: NotificationItem, t: T, label: Label, formatDate: FormatDate): string {
  const name = who(n, t);
  switch (n.type) {
    case "STAGE_CHANGED":
      return t("notif.msg.STAGE_CHANGED", { who: name, state: label("state", n.state) });
    case "ACTION_REQUIRED":
      return n.detail ? `${name}: ${n.detail}` : t("notif.msg.ACTION_REQUIRED", { who: name });
    case "TECH_TEST":
      return n.detail ? `${t("notif.msg.TECH_TEST", { who: name })} — ${n.detail}` : t("notif.msg.TECH_TEST", { who: name });
    case "INTERVIEW_REMINDER":
      return t("notif.msg.INTERVIEW_REMINDER", { who: name, when: formatDate(n.dueAt, true) });
    case "DEADLINE_REMINDER":
      return t("notif.msg.DEADLINE_REMINDER", { who: name, when: formatDate(n.dueAt, true) });
    case "SUGGESTION_TO_REVIEW":
      return t("notif.msg.SUGGESTION_TO_REVIEW", { who: name, state: label("state", n.state) });
    case "SOURCE_FAILING":
      return t("notif.msg.SOURCE_FAILING", { source: n.title ?? "", detail: n.detail ?? "" });
    case "SOURCE_TO_CHECK":
      return t("notif.msg.SOURCE_TO_CHECK", { source: n.title ?? "", kind: n.company ?? "" });
    case "NO_UPDATE":
      return t("notif.msg.NO_UPDATE", { who: name });
    case "NEW_JOBS":
      return t("notif.msg.NEW_JOBS", { count: n.itemCount ?? 0, source: n.title ?? "" });
    case "TEST":
      return t("notif.msg.TEST");
    default:
      return n.message;
  }
}

/** Para onde o aviso leva no painel. Ids vêm da API e são codificados na URL. */
export function notificationLink(n: NotificationItem): string | null {
  if (n.applicationId) {
    return `/candidaturas/${encodeURIComponent(n.applicationId)}`;
  }
  if (n.type === "NEW_JOBS" && n.sourceId) {
    return `/vagas?sourceId=${encodeURIComponent(n.sourceId)}`;
  }
  if (n.sourceId) {
    return "/fontes";
  }
  return null;
}
