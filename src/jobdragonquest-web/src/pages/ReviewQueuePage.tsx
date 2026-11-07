import { useState } from "react";
import { Link } from "react-router";

import { api } from "../api/endpoints";
import type { ApplicationState } from "../api/types";
import { Badge, Button, Card, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";

/**
 * Fila de revisão (issue #112): candidaturas que a coleta recomendou para um perfil no modo "revisar". Aprovar leva
 * para "preparada"; descartar encerra. O envio para a empresa continua sendo da pessoa.
 */
export function ReviewQueuePage() {
  const { t, formatDate, errorMessage } = useI18n();
  const queue = useAsync(() => api.applications({ state: "RECOMMENDED", size: 100 }).then((r) => r.data), []);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [notice, setNotice] = useState<{ tone: "error" | "success"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const decide = async (ids: string[], toState: ApplicationState, evidence: string) => {
    setNotice(null);
    setBusy(true);
    try {
      const { data } = await api.batchTransition({ ids, toState, evidence });
      setNotice({
        tone: data.failed.length > 0 ? "error" : "success",
        text: t("board.batchDone", { updated: data.updated, failed: data.failed.length }),
      });
      setSelected(new Set());
      queue.reload();
    } catch (err) {
      setNotice({ tone: "error", text: errorMessage(err) });
    } finally {
      setBusy(false);
    }
  };

  const approve = (ids: string[]) => void decide(ids, "PREPARED", t("review.approvedReason"));
  const discard = (ids: string[]) => void decide(ids, "CLOSED", t("review.discardedReason"));
  const items = queue.data?.content ?? [];

  return (
    <div className="page">
      <div className="page-header">
        <h1>{t("review.title")}</h1>
      </div>
      <p className="muted">{t("review.intro")}</p>
      {notice && (
        <Notice tone={notice.tone} onClose={() => setNotice(null)}>
          {notice.text}
        </Notice>
      )}
      {queue.error ? <Notice tone="error">{errorMessage(queue.error)}</Notice> : null}
      {queue.loading && !queue.data ? <Loading /> : null}
      {queue.data && items.length === 0 && <p className="muted">{t("review.empty")}</p>}

      {selected.size > 0 && (
        <div className="batch-bar" role="group" aria-label={t("board.selected", { count: selected.size })}>
          <strong>{t("board.selected", { count: selected.size })}</strong>
          <Button variant="primary" disabled={busy} onClick={() => approve([...selected])}>
            {t("review.approveSelected")}
          </Button>
          <Button disabled={busy} onClick={() => discard([...selected])}>
            {t("board.batchDiscard")}
          </Button>
        </div>
      )}

      {items.length > 0 && (
        <Card>
          <ul className="contact-list">
            {items.map((item) => (
              <li key={item.id}>
                <div className="kanban-item">
                  <input
                    type="checkbox"
                    aria-label={t("board.selectCard", { title: item.title ?? item.id })}
                    checked={selected.has(item.id)}
                    onChange={() =>
                      setSelected((current) => {
                        const next = new Set(current);
                        if (next.has(item.id)) next.delete(item.id);
                        else next.add(item.id);
                        return next;
                      })
                    }
                  />
                  <div>
                    <Link to={`/candidaturas/${item.id}`}>{item.title}</Link> · {item.company}{" "}
                    {item.matchScore !== null && <Badge tone="info">{item.matchScore}%</Badge>}
                    <div className="small muted">{formatDate(item.createdAt)}</div>
                    {item.notes && <p className="prewrap small">{item.notes}</p>}
                  </div>
                </div>
                <div className="row-actions">
                  <Button variant="primary" disabled={busy} onClick={() => approve([item.id])}>
                    {t("review.approve", { title: item.title ?? "" })}
                  </Button>
                  <Button disabled={busy} onClick={() => discard([item.id])}>
                    {t("review.discard", { title: item.title ?? "" })}
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}
