import { useState } from "react";
import { Link, useParams, useSearchParams } from "react-router";

import { api } from "../api/endpoints";
import type { ResumeEntry } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { Badge, Button, Card, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";

/**
 * Currículo montado do perfil (issue #118), geral ou ajustado à vaga (?vaga=id): só reordena e destaca o que a vaga
 * pede, nunca inventa. "Imprimir ou salvar em PDF" usa a impressão do próprio navegador, de graça.
 */
export function ResumePage() {
  const { id = "" } = useParams();
  const [search] = useSearchParams();
  const jobId = search.get("vaga") ?? undefined;
  const { user } = useAuth();
  const { t, formatDate, errorMessage } = useI18n();
  const resume = useAsync(() => api.resume(id, jobId).then((r) => r.data), [id, jobId]);
  const letter = useAsync(() => api.coverLetter(id, jobId).then((r) => r.data), [id, jobId]);
  const [copied, setCopied] = useState(false);

  if (resume.loading && !resume.data) return <Loading />;
  if (resume.error || !resume.data) return <Notice tone="error">{errorMessage(resume.error)}</Notice>;
  const r = resume.data;
  const period = (e: ResumeEntry) =>
    [e.startDate ? formatDate(e.startDate) : null, e.current ? t("resume.current") : e.endDate ? formatDate(e.endDate) : null].filter(Boolean).join(" – ");
  const section = (title: string, entries: ResumeEntry[]) =>
    entries.length > 0 && (
      <section className="resume-section">
        <h2>{title}</h2>
        {entries.map((e, index) => (
          <article key={`${e.title}-${index}`} className={e.highlighted ? "resume-entry highlighted" : "resume-entry"}>
            <h3>
              {e.title}
              {e.organization ? ` · ${e.organization}` : ""}
            </h3>
            <p className="small muted">{[period(e), e.location].filter(Boolean).join(" · ")}</p>
            {e.description && <p className="prewrap">{e.description}</p>}
            {e.skills.length > 0 && <p className="small">{e.skills.join(", ")}</p>}
          </article>
        ))}
      </section>
    );

  return (
    <div className="page">
      <div className="page-header no-print">
        <Link to={`/perfis/${id}`} className="small">
          ‹ {t("common.back")}
        </Link>
        <Button variant="primary" onClick={() => window.print()}>
          {t("resume.print")}
        </Button>
      </div>
      {r.jobTitle && <Notice tone="info">{t("resume.tailored", { title: r.jobTitle, company: r.company ?? "" })}</Notice>}
      <article className="resume" aria-label={t("resume.title")}>
        <header>
          <h1>{user?.displayName}</h1>
          {(r.headline || r.currentTitle) && <p className="resume-headline">{r.headline ?? r.currentTitle}</p>}
          <p className="small">{[user?.email, r.location, ...r.links].filter(Boolean).join(" · ")}</p>
        </header>
        {r.summary && (
          <section className="resume-section">
            <h2>{t("resume.summary")}</h2>
            <p className="prewrap">{r.summary}</p>
          </section>
        )}
        {r.skills.length > 0 && (
          <section className="resume-section">
            <h2>{t("resume.skills")}</h2>
            <ul className="resume-skills">
              {r.skills.map((s) => (
                <li key={s.name} className={s.highlighted ? "highlighted" : undefined}>
                  {s.name}
                  {s.time ? ` (${s.time})` : ""}
                  {s.highlighted && (
                    <span className="no-print">
                      {" "}
                      <Badge tone="info">{t("resume.asked")}</Badge>
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </section>
        )}
        {section(t("resume.experience"), r.experience)}
        {section(t("resume.education"), r.education)}
        {section(t("resume.other"), r.other)}
        {r.languages.length > 0 && (
          <section className="resume-section">
            <h2>{t("resume.languages")}</h2>
            <p>{r.languages.join(" · ")}</p>
          </section>
        )}
      </article>

      <Card title={t("resume.letter")} className="no-print">
        {letter.error ? <Notice tone="error">{errorMessage(letter.error)}</Notice> : null}
        {letter.data && (
          <>
            <p className="prewrap">{letter.data.text}</p>
            {letter.data.missing.length > 0 && <Notice tone="info">{t("resume.letterMissing", { fields: letter.data.missing.join(", ") })}</Notice>}
            <Button
              onClick={() =>
                void navigator.clipboard?.writeText(letter.data?.text ?? "").then(
                  () => setCopied(true),
                  () => setCopied(false),
                )
              }
            >
              {copied ? t("assist.copied") : t("resume.copyLetter")}
            </Button>
          </>
        )}
      </Card>
    </div>
  );
}
