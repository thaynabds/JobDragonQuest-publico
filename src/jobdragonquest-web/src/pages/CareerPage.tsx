import { useState } from "react";

import { api } from "../api/endpoints";
import { Badge, Card, Field, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";

/**
 * Carreira (issue #118): competências mais pedidas e faixa salarial a partir das vagas que a própria conta coletou.
 * Nada vem de fora nem custa nada. O roteiro de negociação é texto fixo, não uma previsão.
 */
export function CareerPage() {
  const { t, errorMessage } = useI18n();
  const profiles = useAsync(() => api.profiles().then((r) => r.data), []);
  const [profileId, setProfileId] = useState("");
  const [title, setTitle] = useState("");
  const [applied, setApplied] = useState("");
  const skills = useAsync(() => api.careerSkills({ profileId: profileId || undefined, title: applied || undefined }).then((r) => r.data), [profileId, applied]);
  const salary = useAsync(() => api.careerSalary({ title: applied || undefined }).then((r) => r.data), [applied]);

  return (
    <div className="page">
      <div className="page-header">
        <h1>{t("career.title")}</h1>
      </div>
      <p className="muted">{t("career.intro")}</p>
      <form
        className="filters"
        onSubmit={(event) => {
          event.preventDefault();
          setApplied(title.trim());
        }}
      >
        <Field label={t("board.profile")}>
          <select value={profileId} onChange={(e) => setProfileId(e.target.value)}>
            <option value="">{t("common.none")}</option>
            {(profiles.data ?? []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label={t("career.titleFilter")}>
          <input maxLength={160} value={title} onChange={(e) => setTitle(e.target.value)} />
        </Field>
        <button type="submit" className="btn btn-secondary">
          {t("common.filter")}
        </button>
      </form>

      <Card title={t("career.skills")}>
        {skills.error ? <Notice tone="error">{errorMessage(skills.error)}</Notice> : null}
        {skills.loading && !skills.data ? <Loading /> : null}
        {skills.data && skills.data.length === 0 && <p className="muted small">{t("career.empty")}</p>}
        <ul className="contact-list">
          {(skills.data ?? []).map((s) => (
            <li key={s.skill}>
              <span>
                <strong>{s.skill}</strong> · {t("career.inJobs", { percent: s.percent, jobs: s.jobs })}{" "}
                {profileId && <Badge tone={s.inProfile ? "good" : "warn"}>{t(s.inProfile ? "career.have" : "career.develop")}</Badge>}
              </span>
            </li>
          ))}
        </ul>
      </Card>

      <Card title={t("career.salary")}>
        {salary.error ? <Notice tone="error">{errorMessage(salary.error)}</Notice> : null}
        {salary.data && salary.data.length === 0 && <p className="muted small">{t("career.noSalary")}</p>}
        <ul className="contact-list">
          {(salary.data ?? []).map((s) => (
            <li key={`${s.currency}-${s.period}`}>
              <span>
                <strong>
                  {s.currency} · {t(`career.period.${s.period}`)}
                </strong>{" "}
                {t("career.range", { min: s.min, median: s.median, max: s.max, jobs: s.jobs })}
              </span>
            </li>
          ))}
        </ul>
        <h3>{t("career.negotiation")}</h3>
        <ol className="prewrap">
          <li>{t("career.neg1")}</li>
          <li>{t("career.neg2")}</li>
          <li>{t("career.neg3")}</li>
          <li>{t("career.neg4")}</li>
          <li>{t("career.neg5")}</li>
        </ol>
      </Card>
    </div>
  );
}
