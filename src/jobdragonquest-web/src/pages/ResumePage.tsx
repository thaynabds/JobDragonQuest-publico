import type { MessageKey } from "../i18n/messages";
import { useState } from "react";
import { Link, useParams, useSearchParams } from "react-router";

import { api } from "../api/endpoints";
import type { ResumeEntry } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { Field, Badge, Button, Card, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";

/**
 * Currículo montado do perfil (issue #118), geral ou ajustado à vaga (?vaga=id): só reordena e destaca o que a vaga
 * pede, nunca inventa. "Imprimir ou salvar em PDF" usa a impressão do próprio navegador, de graça.
 */
/** Títulos do currículo no idioma do documento (o da vaga, quando a opção está ligada). */
function headings(language?: string) {
  if (language === "en") return { summary: "Summary", skills: "Skills", experience: "Experience", education: "Education", other: "Courses, projects and certifications", languages: "Languages" };
  if (language === "es") return { summary: "Resumen", skills: "Competencias", experience: "Experiencia", education: "Formación", other: "Cursos, proyectos y certificaciones", languages: "Idiomas" };
  return { summary: "Resumo", skills: "Competências", experience: "Experiência", education: "Formação", other: "Cursos, projetos e certificações", languages: "Idiomas" };
}

/** Dados que só existem na lista extra devolvida pela API. */
const EXTRA_ONLY = new Set(["GENDER", "BIRTH_DATE", "GPA", "NATIONALITY", "POSTAL_CODE", "MILITARY"]);

export function ResumePage() {
  const { id = "" } = useParams();
  const [search] = useSearchParams();
  const jobId = search.get("vaga") ?? undefined;
  const { user } = useAuth();
  const { t, formatDate, errorMessage } = useI18n();
  const resume = useAsync(() => api.resume(id, jobId).then((r) => r.data), [id, jobId]);
  const letter = useAsync(() => api.coverLetter(id, jobId).then((r) => r.data), [id, jobId]);
  const profile = useAsync(() => api.profile(id).then((r) => r.data), [id]);
  const [copied, setCopied] = useState(false);
  const [editedLetter, setEditedLetter] = useState<string | null>(null);

  if (resume.loading && !resume.data) return <Loading />;
  if (resume.error || !resume.data) return <Notice tone="error">{errorMessage(resume.error)}</Notice>;
  const r = resume.data;
  const heading = headings(r.language);
  const options = profile.data?.options;
  const hide = (field: NonNullable<NonNullable<typeof options>["resumeHidden"]>[number]) => options?.resumeHidden?.includes(field) === true;
  const show = (section: NonNullable<NonNullable<typeof options>["resumeVisible"]>[number]) => options?.resumeVisible?.includes(section) === true;
  const letterText = editedLetter ?? letter.data?.text ?? "";
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
      {(r.language ?? "pt") !== "pt" && (
        <div className="no-print">
          <Notice tone={r.needsTranslation ? "info" : "success"}>{r.needsTranslation ? t("resume.needsTranslation") : t("resume.translated", { language: (r.language ?? "").toUpperCase() })}</Notice>
        </div>
      )}
      <article className="resume" aria-label={t("resume.title")}>
        <header>
          <h1>{(show("SOCIAL_NAME") ? options?.socialName : null) ?? user?.displayName}</h1>
          {(r.headline || r.currentTitle) && <p className="resume-headline">{r.headline ?? r.currentTitle}</p>}
          <p className="small">
            {[hide("EMAIL") ? null : show("CONTACT") ? options?.contactEmail ?? user?.email : user?.email, show("CONTACT") && !hide("PHONE") ? [options?.phoneCountryCode, options?.phone].filter(Boolean).join(" ") : null, show("CONTACT") && !hide("SECONDARY_PHONE") ? options?.secondaryPhone : null, r.location, ...r.links]
              .filter(Boolean)
              .join(" · ")}
          </p>
        </header>
        {r.summary && (
          <section className="resume-section">
            <h2>{heading.summary}</h2>
            <p className="prewrap">{r.summary}</p>
          </section>
        )}
        {r.skills.length > 0 && (
          <section className="resume-section">
            <h2>{heading.skills}</h2>
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
        {section(heading.experience, r.experience)}
        {section(heading.education, r.education)}
        {section(heading.other, r.other)}
        {r.languages.length > 0 && (
          <section className="resume-section">
            <h2>{heading.languages}</h2>
            <p>{r.languages.join(" · ")}</p>
          </section>
        )}
        {(r.extras ?? []).filter((extra) => EXTRA_ONLY.has(extra.section)).map((extra) => (
          <section className="resume-section" key={extra.section}>
            <h2>{t(`options.section.${extra.section}` as MessageKey)}</h2>
            <p>{extra.values.join(" · ")}</p>
          </section>
        ))}
        {show("SOFT_SKILLS") && (options?.softSkills?.length ?? 0) > 0 && (
          <section className="resume-section">
            <h2>{t("options.softSkills")}</h2>
            <p>{options?.softSkills?.join(" · ")}</p>
          </section>
        )}
        {show("WORK_PREFERENCES") && (
          <section className="resume-section">
            <h2>{t("options.section.WORK_PREFERENCES")}</h2>
            <p>{[options?.workload, profile.data?.availability, options?.transportAid ? t("options.transportAid") : null].filter(Boolean).join(" · ")}</p>
          </section>
        )}
        {show("SALARY") && (options?.salaryMin != null || options?.salaryMax != null) && (
          <section className="resume-section">
            <h2>{t("options.section.SALARY")}</h2>
            <p>{[options?.salaryMin, options?.salaryMax].filter((v) => v != null).join(" – ")}</p>
          </section>
        )}
        {show("DIVERSITY") && ((options?.diversity?.length ?? 0) > 0 || (options?.diversityOther?.length ?? 0) > 0) && (
          <section className="resume-section">
            <h2>{t("options.diversity")}</h2>
            <p>{[...(options?.diversity ?? []).map((d) => t(`options.diversity.${d}` as MessageKey)), ...(options?.diversityOther ?? [])].join(" · ")}</p>
          </section>
        )}
        {show("CONTACT_HOURS") && (options?.contactHours?.length ?? 0) > 0 && (
          <section className="resume-section">
            <h2>{t("options.contactHours")}</h2>
            <p>{options?.contactHours?.map((h) => `${t(`options.day.${h.day}` as MessageKey)} ${h.slots.map((x) => `${x.start}–${x.end}`).join(", ")}`).join(" · ")}</p>
          </section>
        )}
      </article>

      <Card title={t("resume.letter")} className="no-print">
        {letter.error ? <Notice tone="error">{errorMessage(letter.error)}</Notice> : null}
        {letter.data && (
          <>
            <Field label={t("resume.editLetter")} hint={t("resume.editLetterHint")} wide>
              <textarea rows={12} maxLength={8000} value={letterText} onChange={(e) => setEditedLetter(e.target.value)} />
            </Field>
            {letter.data.missing.length > 0 && <Notice tone="info">{t("resume.letterMissing", { fields: letter.data.missing.join(", ") })}</Notice>}
            <Button
              onClick={() =>
                void navigator.clipboard?.writeText(letterText).then(
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
