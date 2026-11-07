import { useState } from "react";
import { Link } from "react-router";

import { api } from "../api/endpoints";
import type { AssistReview, InterviewQuestion, SuggestedAnswer } from "../api/types";
import { Badge, Button, Card, Field, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";

const TONE = { OK: "good", WARN: "warn", BLOCK: "bad" } as const;

/**
 * Perguntas e respostas sugeridas e revisão antes de enviar (issue #115). Tudo vem do perfil escolhido: o que falta
 * aparece como pendência para completar, nunca inventado.
 */
export function AssistCard({ jobId, profiles }: { jobId: string; profiles: { id: string; name: string }[] }) {
  const { t, label, errorMessage } = useI18n();
  const [profileId, setProfileId] = useState(profiles[0]?.id ?? "");
  const [answers, setAnswers] = useState<SuggestedAnswer[] | null>(null);
  const [review, setReview] = useState<AssistReview | null>(null);
  const [interview, setInterview] = useState<InterviewQuestion[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState<number | null>(null);

  const run = async (action: () => Promise<void>) => {
    setError(null);
    try {
      await action();
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  if (profiles.length === 0) {
    return null;
  }
  return (
    <Card title={t("assist.title")}>
      <div className="filters">
        <Field label={t("board.profile")}>
          <select value={profileId} onChange={(e) => setProfileId(e.target.value)}>
            {profiles.map((profile) => (
              <option key={profile.id} value={profile.id}>
                {profile.name}
              </option>
            ))}
          </select>
        </Field>
        <Button onClick={() => void run(async () => setAnswers((await api.assistAnswers(jobId, profileId)).data))}>{t("assist.analyze")}</Button>
        <Button variant="primary" onClick={() => void run(async () => setReview((await api.assistReview(jobId, profileId)).data))}>
          {t("assist.review")}
        </Button>
        <Button onClick={() => void run(async () => setInterview((await api.interview(jobId, profileId)).data))}>{t("assist.interview")}</Button>
        <Link className="btn btn-secondary" to={`/perfis/${encodeURIComponent(profileId)}/curriculo?vaga=${encodeURIComponent(jobId)}`}>
          {t("assist.resume")}
        </Link>
      </div>
      {error && <Notice tone="error">{error}</Notice>}

      {review && (
        <section aria-label={t("assist.review")}>
          <Notice tone={review.ready ? "success" : "error"}>{t(review.ready ? "assist.ready" : "assist.notReady")}</Notice>
          <ul className="contact-list">
            {review.checks.map((check) => (
              <li key={check.code}>
                <span>
                  <Badge tone={TONE[check.status]}>{label("checkStatus", check.status)}</Badge> {check.message}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {interview && (
        <section aria-label={t("assist.interview")}>
          <ul className="contact-list">
            {interview.map((item, index) => (
              <li key={`${item.category}-${index}`}>
                <strong>{item.question}</strong>
                <span className="small">{item.hint}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {answers && (
        <section aria-label={t("assist.analyze")}>
          <p className="muted small">{t("assist.answersHint")}</p>
          <ul className="contact-list">
            {answers.map((item, index) => (
              <li key={`${item.category}-${index}`}>
                <strong>{item.question}</strong>
                {item.answer ? (
                  <>
                    <p className="prewrap">{item.answer}</p>
                    <Button
                      onClick={() =>
                        void navigator.clipboard?.writeText(item.answer ?? "").then(
                          () => setCopied(index),
                          () => setCopied(null),
                        )
                      }
                    >
                      {copied === index ? t("assist.copied") : t("assist.copy")}
                    </Button>
                  </>
                ) : (
                  <Badge tone="warn">{item.missing}</Badge>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
    </Card>
  );
}
