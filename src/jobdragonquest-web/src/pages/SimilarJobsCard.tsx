import { useState } from "react";
import { Link } from "react-router";

import { api } from "../api/endpoints";
import type { SimilarBasis } from "../api/types";
import { Badge, Card, Field, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";

/** Vagas parecidas com as favoritas ou com as últimas candidaturas enviadas (issue #110). */
export function SimilarJobsCard({ refreshKey }: { refreshKey: number }) {
  const { t, errorMessage } = useI18n();
  const [basis, setBasis] = useState<SimilarBasis>("FAVORITES");
  const [minScore, setMinScore] = useState(30);
  const similar = useAsync(() => api.similarJobs({ basis, minScore, limit: 10 }).then((r) => r.data), [basis, minScore, refreshKey]);

  return (
    <Card title={t("similar.title")}>
      <div className="filters">
        <Field label={t("similar.basis")}>
          <select value={basis} onChange={(e) => setBasis(e.target.value as SimilarBasis)}>
            <option value="FAVORITES">{t("similar.basis.FAVORITES")}</option>
            <option value="APPLIED">{t("similar.basis.APPLIED")}</option>
          </select>
        </Field>
        <Field label={t("similar.minScore", { value: minScore })}>
          <input type="range" min={0} max={100} step={5} value={minScore} onChange={(e) => setMinScore(Number(e.target.value))} />
        </Field>
      </div>
      {similar.error ? <Notice tone="error">{errorMessage(similar.error)}</Notice> : null}
      {similar.loading && !similar.data ? <Loading /> : null}
      {similar.data && similar.data.length === 0 && (
        <p className="muted small">{t(basis === "FAVORITES" ? "similar.emptyFavorites" : "similar.emptyApplied")}</p>
      )}
      <ul className="contact-list">
        {(similar.data ?? []).map((item) => (
          <li key={item.job.id}>
            <div>
              <Link to={`/vagas/${item.job.id}`}>{item.job.title}</Link> · {item.job.company}{" "}
              <Badge tone="info">{t("similar.score", { score: item.score })}</Badge>
            </div>
            <span className="small muted">
              {t("similar.because", { title: item.basisTitle })}
              {item.sharedWords.length > 0 && ` · ${t("similar.words")}: ${item.sharedWords.join(", ")}`}
              {item.sharedSkills.length > 0 && ` · ${t("similar.skills")}: ${item.sharedSkills.join(", ")}`}
            </span>
          </li>
        ))}
      </ul>
    </Card>
  );
}
