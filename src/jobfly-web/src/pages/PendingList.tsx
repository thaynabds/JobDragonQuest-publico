import { useState } from "react";
import { Link } from "react-router";

import { api } from "../api/endpoints";
import type { PendingItem } from "../api/types";
import { Badge, Button, Empty, ExternalLink, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";

const TONE = { HIGH: "bad", MEDIUM: "warn", LOW: "info" } as const;

/** Central de pendências: o que depende da pessoa, com a ação ao lado. */
export function PendingList({ onChange }: { onChange?: () => void }) {
  const { t, label, formatDate, errorMessage } = useI18n();
  const pending = useAsync(() => api.pending().then((r) => r.data), []);
  const [message, setMessage] = useState<{ tone: "error" | "success"; text: string } | null>(null);

  const act = async (action: () => Promise<unknown>, success: string) => {
    try {
      await action();
      setMessage({ tone: "success", text: success });
      pending.reload();
      onChange?.();
    } catch (err) {
      setMessage({ tone: "error", text: errorMessage(err) });
    }
  };

  const actions = (item: PendingItem) => {
    if (item.type === "SUGGESTION_TO_REVIEW" && item.applicationId && item.eventId) {
      const applicationId = item.applicationId;
      const eventId = item.eventId;
      return (
        <>
          <Button variant="primary" onClick={() => void act(() => api.acceptSuggestion(applicationId, eventId), t("application.applied"))}>
            {t("dashboard.accept")}
          </Button>
          <Button onClick={() => void act(() => api.dismissSuggestion(applicationId, eventId), t("common.saved"))}>{t("dashboard.dismiss")}</Button>
        </>
      );
    }
    if (item.type === "SOURCE_TO_CHECK" && item.sourceId) {
      const sourceId = item.sourceId;
      return (
        <>
          <ExternalLink href={item.url}>{t("common.open")}</ExternalLink>
          <Button onClick={() => void act(() => api.checkSource(sourceId, { jobsRegistered: 0, note: null }), t("sources.checked"))}>
            {t("dashboard.markChecked")}
          </Button>
        </>
      );
    }
    if (item.sourceId) {
      return <Link to="/fontes">{t("common.open")}</Link>;
    }
    return item.applicationId ? <Link to={`/candidaturas/${item.applicationId}`}>{t("common.open")}</Link> : null;
  };

  if (pending.loading && !pending.data) {
    return <Loading />;
  }
  return (
    <div>
      {message && (
        <Notice tone={message.tone} onClose={() => setMessage(null)}>
          {message.text}
        </Notice>
      )}
      {pending.error ? <Notice tone="error">{errorMessage(pending.error)}</Notice> : null}
      {pending.data && pending.data.length === 0 && <Empty>{t("dashboard.noPending")}</Empty>}
      <ul className="pending-list">
        {pending.data?.map((item) => (
          <li key={`${item.type}-${item.applicationId ?? ""}-${item.sourceId ?? ""}-${item.eventId ?? ""}`} className="pending-item">
            <div className="pending-main">
              <Badge tone={TONE[item.priority]}>{label("pending", item.type)}</Badge>
              <div>
                <strong>{item.title}</strong> <span className="muted">· {item.company}</span>
                <p className="pending-message">{item.message}</p>
                {item.dueAt && <span className="muted small">{formatDate(item.dueAt, true)}</span>}
              </div>
            </div>
            <div className="row-actions">{actions(item)}</div>
          </li>
        ))}
      </ul>
    </div>
  );
}
