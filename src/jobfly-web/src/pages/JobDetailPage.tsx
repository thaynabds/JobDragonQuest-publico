import { useState } from "react";
import { useNavigate, useParams } from "react-router";

import { ApiError } from "../api/client";
import { api } from "../api/endpoints";
import type { MatchResult, Outcome, Recommendation } from "../api/types";
import { Badge, Button, Card, Empty, ExternalLink, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { newIdempotencyKey } from "../utils/safe";
import { useAsync } from "../utils/useAsync";

const OUTCOME_TONE: Record<Outcome, "good" | "warn" | "bad" | "neutral"> = {
  MET: "good",
  PARTIAL: "warn",
  UNMET: "bad",
  NOT_EVALUATED: "neutral",
};
const RECOMMENDATION_TONE: Record<Recommendation, "good" | "warn" | "bad" | "neutral"> = {
  APPLY: "good",
  REVIEW: "warn",
  SKIP: "bad",
  INSUFFICIENT_DATA: "neutral",
};

export function JobDetailPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const { t, label, formatDate, errorMessage } = useI18n();
  const job = useAsync(() => api.job(id).then((r) => r.data), [id]);
  const matches = useAsync(() => api.matches(id).then((r) => r.data), [id]);
  const [error, setError] = useState<string | null>(null);

  const apply = async (match: MatchResult) => {
    setError(null);
    try {
      const { data } = await api.createApplication(
        { origin: "MANUAL", jobId: id, profileId: match.profileId, state: "SUBMITTED" },
        newIdempotencyKey("job"),
      );
      navigate(`/candidaturas/${data.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? errorMessage(err) : t("error.generic"));
    }
  };

  if (job.loading && !job.data) {
    return <Loading />;
  }
  if (job.error || !job.data) {
    return <Notice tone="error">{errorMessage(job.error)}</Notice>;
  }
  const data = job.data;
  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>{data.title}</h1>
          <p className="muted">
            {data.company} · {data.location ?? t("common.none")} {data.workMode && <Badge>{label("workMode", data.workMode)}</Badge>}{" "}
            {data.seniority && <Badge tone="info">{label("seniority", data.seniority)}</Badge>}
          </p>
        </div>
        <ExternalLink href={data.url}>{t("application.open")}</ExternalLink>
      </div>
      {error && <Notice tone="error">{error}</Notice>}

      <Card title={t("jobs.matches")}>
        {matches.loading && !matches.data ? <Loading /> : null}
        {matches.error ? <Notice tone="error">{errorMessage(matches.error)}</Notice> : null}
        {matches.data && matches.data.length === 0 && <Empty>{t("jobs.noProfiles")}</Empty>}
        {matches.data?.map((match, index) => (
          <article key={match.profileId} className={`match${index === 0 ? " match-best" : ""}`}>
            <header className="match-header">
              <div>
                <strong>{match.profileName}</strong>{" "}
                <Badge tone={RECOMMENDATION_TONE[match.recommendation]}>{label("recommendation", match.recommendation)}</Badge>
              </div>
              <span className="score">{match.score === null ? "—" : `${match.score}%`}</span>
            </header>
            <p className="muted">{match.summary}</p>
            {match.blockers.length > 0 && (
              <div>
                <h4>{t("jobs.blockers")}</h4>
                <ul>
                  {match.blockers.map((blocker) => (
                    <li key={blocker.code}>{blocker.message}</li>
                  ))}
                </ul>
              </div>
            )}
            {match.strengths.length > 0 && (
              <p>
                <strong>{t("jobs.strengths")}:</strong> {match.strengths.join(" · ")}
              </p>
            )}
            {match.warnings.length > 0 && (
              <p className="muted">
                <strong>{t("jobs.warnings")}:</strong> {match.warnings.join(" · ")}
              </p>
            )}
            <ul className="requirements">
              {match.requirements.map((requirement, i) => (
                <li key={`${requirement.text}-${i}`}>
                  <Badge tone={OUTCOME_TONE[requirement.outcome]}>{label("outcome", requirement.outcome)}</Badge> {requirement.text}{" "}
                  <span className="muted small">— {requirement.reason}</span>
                </li>
              ))}
            </ul>
            <Button variant={index === 0 ? "primary" : "secondary"} onClick={() => void apply(match)}>
              {t("jobs.applyWith")}
            </Button>
          </article>
        ))}
      </Card>

      {data.description && (
        <Card title={t("jobs.description")}>
          <p className="prewrap">{data.description}</p>
        </Card>
      )}
      <p className="muted small">
        {t("jobs.foundAt")}: {formatDate(data.foundAt, true)} · {data.source}
      </p>
    </div>
  );
}
