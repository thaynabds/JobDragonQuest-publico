import { useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router";

import { api } from "../api/endpoints";
import { APPLICATION_STATES, type ApplicationState } from "../api/types";
import { Badge, Button, Card, ExternalLink, Field, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";
import { ContactsSection } from "./ContactsSection";

export function ApplicationDetailPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const { t, label, formatDate, errorMessage } = useI18n();
  const detail = useAsync(() => api.application(id).then((r) => r.data), [id]);
  const [toState, setToState] = useState<ApplicationState>("SCREENING");
  const [evidence, setEvidence] = useState("");
  const [nextAction, setNextAction] = useState("");
  const [note, setNote] = useState("");
  const [notice, setNotice] = useState<{
    tone: "error" | "success";
    text: string;
  } | null>(null);

  const act = async (action: () => Promise<string>) => {
    setNotice(null);
    try {
      setNotice({ tone: "success", text: await action() });
      detail.reload();
    } catch (err) {
      setNotice({ tone: "error", text: errorMessage(err) });
    }
  };

  const transition = (event: FormEvent) => {
    event.preventDefault();
    void act(async () => {
      const { data } = await api.transition(id, {
        toState,
        source: "MANUAL",
        evidence: evidence.trim(),
        nextAction: nextAction.trim() || null,
      });
      setEvidence("");
      setNextAction("");
      return data.outcome === "APPLIED" ? t("application.applied") : data.outcome === "SUGGESTED" ? t("application.suggested") : t("application.noted");
    });
  };

  const addNote = (event: FormEvent) => {
    event.preventDefault();
    void act(async () => {
      await api.addNote(id, note.trim());
      setNote("");
      return t("common.saved");
    });
  };

  if (detail.loading && !detail.data) {
    return <Loading />;
  }
  if (detail.error || !detail.data) {
    return <Notice tone="error">{errorMessage(detail.error)}</Notice>;
  }
  const { application: app, events } = detail.data;
  const pending = events.filter((event) => event.kind === "SUGGESTION" && event.status === "PENDING");

  return (
    <div className="page">
      <Link to="/candidaturas" className="small">
        ‹ {t("common.back")}
      </Link>
      <div className="page-header">
        <div>
          <h1>{app.title}</h1>
          <p className="muted">
            {app.company} · {app.platform ?? label("origin", app.origin)} <Badge tone="info">{label("state", app.state)}</Badge>{" "}
            {app.stale && <Badge tone="warn">{t("board.stale")}</Badge>}
          </p>
        </div>
        <div className="row-actions">
          <ExternalLink href={app.url}>{t("application.open")}</ExternalLink>
          {app.jobId && <Link to={`/vagas/${app.jobId}`}>{t("jobs.matches")}</Link>}
          <Button
            variant="danger"
            onClick={() => {
              if (window.confirm(t("common.confirmDelete"))) {
                void api.deleteApplication(id).then(
                  () => navigate("/candidaturas", { replace: true }),
                  (err: unknown) => setNotice({ tone: "error", text: errorMessage(err) }),
                );
              }
            }}
          >
            {t("common.delete")}
          </Button>
        </div>
      </div>
      {notice && (
        <Notice tone={notice.tone} onClose={() => setNotice(null)}>
          {notice.text}
        </Notice>
      )}

      <div className="facts">
        <div>
          <span className="muted small">{t("board.applied")}</span>
          <span>{formatDate(app.appliedAt)}</span>
        </div>
        <div>
          <span className="muted small">{t("board.updated")}</span>
          <span>{formatDate(app.lastEvidenceAt, true)}</span>
        </div>
        <div>
          <span className="muted small">{t("board.score")}</span>
          <span>{app.matchScore === null ? t("common.none") : `${app.matchScore}%`}</span>
        </div>
        <div>
          <span className="muted small">{t("board.next")}</span>
          <span>{app.nextAction ?? t("common.none")}</span>
        </div>
      </div>
      {app.notes && <p className="prewrap">{app.notes}</p>}

      {pending.map((suggestion) => (
        <Notice key={suggestion.id} tone="info">
          <strong>{t("application.suggestion")}:</strong> {label("state", suggestion.toState)} — {suggestion.evidence} ({t("application.confidence")}{" "}
          {Math.round(suggestion.confidence * 100)}%){" "}
          <Button variant="primary" onClick={() => void act(async () => (await api.acceptSuggestion(id, suggestion.id), t("application.applied")))}>
            {t("dashboard.accept")}
          </Button>{" "}
          <Button onClick={() => void act(async () => (await api.dismissSuggestion(id, suggestion.id), t("common.saved")))}>{t("dashboard.dismiss")}</Button>
        </Notice>
      ))}

      <div className="two-columns">
        <Card title={t("application.changeState")}>
          <form onSubmit={transition}>
            <Field label={t("application.state")}>
              <select value={toState} onChange={(e) => setToState(e.target.value as ApplicationState)}>
                {APPLICATION_STATES.map((state) => (
                  <option key={state} value={state}>
                    {label("state", state)}
                  </option>
                ))}
              </select>
            </Field>
            <Field label={t("application.evidence")} hint={t("application.evidenceHint")}>
              <textarea rows={3} required maxLength={4000} value={evidence} onChange={(e) => setEvidence(e.target.value)} />
            </Field>
            <Field label={t("board.next")}>
              <input maxLength={500} value={nextAction} onChange={(e) => setNextAction(e.target.value)} />
            </Field>
            <Button type="submit" variant="primary" disabled={!evidence.trim()}>
              {t("common.save")}
            </Button>
          </form>
        </Card>
        <Card title={t("application.addNote")}>
          <form onSubmit={addNote}>
            <Field label={t("application.note")}>
              <textarea rows={3} required maxLength={4000} value={note} onChange={(e) => setNote(e.target.value)} />
            </Field>
            <Button type="submit" disabled={!note.trim()}>
              {t("common.save")}
            </Button>
          </form>
        </Card>
      </div>

      <ContactsSection applicationId={id} onChanged={detail.reload} />

      <Card title={t("application.history")}>
        <ol className="timeline">
          {[...events].reverse().map((event) => (
            <li key={event.id} className={`timeline-${event.kind.toLowerCase()}`}>
              <div className="timeline-head">
                <Badge tone={event.kind === "SUGGESTION" ? "warn" : event.kind === "NOTE" ? "neutral" : "info"}>
                  {event.kind === "STATE_CHANGE"
                    ? label("state", event.toState)
                    : event.kind === "SUGGESTION"
                      ? t("application.suggestion")
                      : t("application.note")}
                </Badge>
                <span className="muted small">
                  {formatDate(event.occurredAt, true)} · {t("application.source")}: {label("eventSource", event.source)} · {t("application.confidence")}{" "}
                  {Math.round(event.confidence * 100)}%{event.kind === "SUGGESTION" ? ` · ${event.status}` : ""}
                </span>
              </div>
              {/* A evidência pode ter vindo de um e-mail: é exibida como texto, nunca como HTML. */}
              <p className="prewrap">{event.evidence}</p>
            </li>
          ))}
        </ol>
      </Card>
    </div>
  );
}
