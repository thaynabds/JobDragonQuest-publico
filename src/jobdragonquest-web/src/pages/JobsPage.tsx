import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";

import { api } from "../api/endpoints";
import { SENIORITIES, WORK_MODES, type JobInput, type Requirement, type Seniority, type WorkMode } from "../api/types";
import { Badge, Button, Card, Empty, ExternalLink, Field, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";
import { SimilarJobsCard } from "./SimilarJobsCard";

const blank = (value: string) => (value.trim() === "" ? null : value.trim());

const EMPTY: JobInput = {
  source: "MANUAL",
  url: null,
  company: "",
  title: "",
  location: null,
  country: null,
  workMode: null,
  seniority: null,
  description: null,
  requirements: [],
  languages: [],
};

export function JobsPage() {
  const { t, label, formatDate, errorMessage } = useI18n();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const jobs = useAsync(() => api.jobs({ page, size: 20, favorite: favoritesOnly || undefined }).then((r) => r.data), [page, favoritesOnly]);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<JobInput>(EMPTY);
  const [error, setError] = useState<string | null>(null);

  const toggleFavorite = async (jobId: string, favorite: boolean) => {
    setError(null);
    try {
      await api.setFavorite(jobId, favorite);
      jobs.reload();
      setRefreshKey((value) => value + 1);
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  const [link, setLink] = useState("");
  const [reading, setReading] = useState(false);
  const [linkNote, setLinkNote] = useState<string | null>(null);

  const readLink = async () => {
    setError(null);
    setLinkNote(null);
    setReading(true);
    try {
      const { data } = await api.jobFromLink(link.trim());
      setForm({ ...EMPTY, ...data.job });
      setLinkNote(t(data.extractedFrom === "JSON_LD" ? "jobs.fromLinkStructured" : "jobs.fromLinkMeta"));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setReading(false);
    }
  };

  const patch = (change: Partial<JobInput>) => setForm((current) => ({ ...current, ...change }));
  const setRequirement = (index: number, change: Partial<Requirement>) =>
    patch({
      requirements: form.requirements.map((r, i) => (i === index ? { ...r, ...change } : r)),
    });

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    try {
      const { data } = await api.createJob({
        ...form,
        company: form.company.trim(),
        title: form.title.trim(),
        requirements: form.requirements.filter((r) => r.text.trim() !== ""),
      });
      navigate(`/vagas/${data.id}`);
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>{t("jobs.title")}</h1>
        <Button variant="primary" onClick={() => setShowForm(!showForm)}>
          {t("jobs.new")}
        </Button>
      </div>

      {showForm && (
        <Card title={t("jobs.new")}>
          <div className="filters" role="group" aria-label={t("jobs.fromLink")}>
            <Field label={t("jobs.fromLink")} hint={t("jobs.fromLinkHint")}>
              <input type="url" maxLength={2048} value={link} onChange={(e) => setLink(e.target.value)} />
            </Field>
            <Button type="button" disabled={!link.trim() || reading} onClick={() => void readLink()}>
              {t("jobs.fromLinkButton")}
            </Button>
          </div>
          {linkNote && <Notice tone="info">{linkNote}</Notice>}
          <form onSubmit={(event) => void submit(event)}>
            {error && <Notice tone="error">{error}</Notice>}
            <div className="form-grid">
              <Field label={t("board.company")}>
                <input value={form.company} required maxLength={200} onChange={(e) => patch({ company: e.target.value })} />
              </Field>
              <Field label={t("application.title")}>
                <input value={form.title} required maxLength={200} onChange={(e) => patch({ title: e.target.value })} />
              </Field>
              <Field label={t("application.url")} wide>
                <input type="url" value={form.url ?? ""} maxLength={2048} onChange={(e) => patch({ url: blank(e.target.value) })} />
              </Field>
              <Field label={t("jobs.source")}>
                <select value={form.source} onChange={(e) => patch({ source: e.target.value })}>
                  {["MANUAL", "LINKEDIN", "GUPY", "INDEED", "INSTAGRAM", "FACEBOOK", "COMPANY_SITE", "EMAIL", "REFERRAL", "OTHER"].map((source) => (
                    <option key={source} value={source}>
                      {source}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={t("jobs.location")}>
                <input value={form.location ?? ""} maxLength={200} onChange={(e) => patch({ location: blank(e.target.value) })} />
              </Field>
              <Field label={t("board.country")}>
                <input value={form.country ?? ""} maxLength={2} onChange={(e) => patch({ country: blank(e.target.value.toUpperCase()) })} />
              </Field>
              <Field label={t("jobs.workMode")}>
                <select
                  value={form.workMode ?? ""}
                  onChange={(e) =>
                    patch({
                      workMode: (e.target.value || null) as WorkMode | null,
                    })
                  }
                >
                  <option value="">{t("common.none")}</option>
                  {WORK_MODES.map((mode) => (
                    <option key={mode} value={mode}>
                      {label("workMode", mode)}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={t("jobs.seniority")}>
                <select
                  value={form.seniority ?? ""}
                  onChange={(e) =>
                    patch({
                      seniority: (e.target.value || null) as Seniority | null,
                    })
                  }
                >
                  <option value="">{t("common.none")}</option>
                  {SENIORITIES.map((s) => (
                    <option key={s} value={s}>
                      {label("seniority", s)}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={t("jobs.description")} wide>
                <textarea rows={4} maxLength={20000} value={form.description ?? ""} onChange={(e) => patch({ description: blank(e.target.value) })} />
              </Field>
            </div>
            <h3>{t("jobs.requirements")}</h3>
            <div className="table-scroll"><table className="table">
              <tbody>
                {form.requirements.map((requirement, index) => (
                  <tr key={index}>
                    <td>
                      <select
                        aria-label={t("jobs.requirements")}
                        value={requirement.kind}
                        onChange={(e) =>
                          setRequirement(index, {
                            kind: e.target.value as Requirement["kind"],
                          })
                        }
                      >
                        <option value="MANDATORY">{t("jobs.mandatory")}</option>
                        <option value="DESIRED">{t("jobs.desired")}</option>
                      </select>
                    </td>
                    <td>
                      <input
                        aria-label={t("jobs.requirementText")}
                        placeholder={t("jobs.requirementText")}
                        value={requirement.text}
                        maxLength={500}
                        onChange={(e) => setRequirement(index, { text: e.target.value })}
                      />
                    </td>
                    <td>
                      <input
                        aria-label={t("jobs.skill")}
                        placeholder={t("jobs.skill")}
                        value={requirement.skill ?? ""}
                        maxLength={80}
                        onChange={(e) =>
                          setRequirement(index, {
                            skill: blank(e.target.value),
                          })
                        }
                      />
                    </td>
                    <td>
                      <input
                        aria-label={t("jobs.minYears")}
                        placeholder={t("jobs.minYears")}
                        type="number"
                        min={0}
                        max={30}
                        step="0.5"
                        value={requirement.minYears ?? ""}
                        onChange={(e) =>
                          setRequirement(index, {
                            minYears: e.target.value === "" ? null : Number(e.target.value),
                          })
                        }
                      />
                    </td>
                    <td>
                      <Button
                        variant="ghost"
                        onClick={() =>
                          patch({
                            requirements: form.requirements.filter((_, i) => i !== index),
                          })
                        }
                      >
                        {t("common.remove")}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table></div>
            <div className="row-actions">
              <Button
                onClick={() =>
                  patch({
                    requirements: [
                      ...form.requirements,
                      {
                        kind: "MANDATORY",
                        text: "",
                        skill: null,
                        minYears: null,
                      },
                    ],
                  })
                }
              >
                {t("common.add")}
              </Button>
              <Button type="submit" variant="primary">
                {t("common.save")}
              </Button>
            </div>
          </form>
        </Card>
      )}

      <SimilarJobsCard refreshKey={refreshKey} />

      <label className="check">
        <input
          type="checkbox"
          checked={favoritesOnly}
          onChange={(e) => {
            setFavoritesOnly(e.target.checked);
            setPage(0);
          }}
        />
        {t("jobs.favoritesOnly")}
      </label>

      {jobs.error ? <Notice tone="error">{errorMessage(jobs.error)}</Notice> : null}
      {jobs.loading && !jobs.data ? <Loading /> : null}
      {jobs.data && jobs.data.content.length === 0 && <Empty />}
      {jobs.data && jobs.data.content.length > 0 && (
        <Card>
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>
                    <span className="visually-hidden">{t("jobs.favorite")}</span>
                  </th>
                  <th>{t("application.title")}</th>
                  <th>{t("board.company")}</th>
                  <th>{t("jobs.location")}</th>
                  <th>{t("jobs.source")}</th>
                  <th>{t("jobs.foundAt")}</th>
                </tr>
              </thead>
              <tbody>
                {jobs.data.content.map((job) => (
                  <tr key={job.id}>
                    <td>
                      <button
                        type="button"
                        className="star"
                        aria-pressed={job.favorite}
                        aria-label={t(job.favorite ? "jobs.unfavorite" : "jobs.favoriteThis", { title: job.title })}
                        onClick={() => void toggleFavorite(job.id, !job.favorite)}
                      >
                        {job.favorite ? "★" : "☆"}
                      </button>
                    </td>
                    <td>
                      <Link to={`/vagas/${job.id}`}>{job.title}</Link> {job.status !== "OPEN" && <Badge tone="warn">{job.status}</Badge>}
                      {job.watchedSourceId && <Badge tone="info">{t("jobs.fromSource")}</Badge>}
                    </td>
                    <td>{job.company}</td>
                    <td className="small">
                      {job.location ?? t("common.none")} {job.workMode && <Badge>{label("workMode", job.workMode)}</Badge>}
                    </td>
                    <td className="small">
                      {job.source} <ExternalLink href={job.url}>↗</ExternalLink>
                    </td>
                    <td className="small">{formatDate(job.foundAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="pager">
            <Button disabled={page === 0} onClick={() => setPage(page - 1)}>
              ‹
            </Button>
            <span className="muted">
              {jobs.data.page + 1} / {Math.max(jobs.data.totalPages, 1)}
            </span>
            <Button disabled={page + 1 >= jobs.data.totalPages} onClick={() => setPage(page + 1)}>
              ›
            </Button>
          </div>
        </Card>
      )}
    </div>
  );
}
