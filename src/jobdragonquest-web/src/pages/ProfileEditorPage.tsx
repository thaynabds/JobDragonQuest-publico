import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router";

import { api } from "../api/endpoints";
import {
  ENTRY_KINDS,
  LANGUAGE_LEVELS,
  SENIORITIES,
  WORK_MODES,
  type Entry,
  type Language,
  type ProfileInput,
  type Skill,
  type WorkMode,
} from "../api/types";
import { Button, Card, Field, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { splitList } from "../utils/safe";

const EMPTY: ProfileInput = {
  name: "",
  objective: null,
  seniority: "JUNIOR",
  minMatchScore: 60,
  salaryExpectation: null,
  salaryCurrency: "BRL",
  availability: null,
  restrictions: null,
  preferredLanguage: "pt-BR",
  desiredTitles: [],
  locations: [],
  workModes: ["REMOTE"],
  skills: [],
  languages: [{ code: "pt", level: "NATIVE" }],
  entries: [],
};

const blank = (value: string) => (value.trim() === "" ? null : value.trim());

export function ProfileEditorPage() {
  const { id } = useParams();
  const isNew = !id || id === "novo";
  const navigate = useNavigate();
  const { t, label, errorMessage } = useI18n();
  const [form, setForm] = useState<ProfileInput>(EMPTY);
  const [titles, setTitles] = useState("");
  const [locations, setLocations] = useState("");
  const [loading, setLoading] = useState(!isNew);
  const [error, setError] = useState<string | null>(null);
  const [details, setDetails] = useState<string[]>([]);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (isNew || !id) {
      return;
    }
    api
      .profile(id)
      .then(({ data }) => {
        setForm(data);
        setTitles(data.desiredTitles.join(", "));
        setLocations(data.locations.join(", "));
      })
      .catch((err: unknown) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, [id, isNew, errorMessage]);

  const patch = (change: Partial<ProfileInput>) => setForm((current) => ({ ...current, ...change }));
  const setSkill = (index: number, change: Partial<Skill>) =>
    patch({ skills: form.skills.map((skill, i) => (i === index ? { ...skill, ...change } : skill)) });
  const setLanguage = (index: number, change: Partial<Language>) =>
    patch({ languages: form.languages.map((language, i) => (i === index ? { ...language, ...change } : language)) });
  const setEntry = (index: number, change: Partial<Entry>) =>
    patch({ entries: form.entries.map((entry, i) => (i === index ? { ...entry, ...change } : entry)) });
  const toggleMode = (mode: WorkMode) =>
    patch({ workModes: form.workModes.includes(mode) ? form.workModes.filter((m) => m !== mode) : [...form.workModes, mode] });

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setDetails([]);
    setSaved(false);
    const body: ProfileInput = {
      ...form,
      name: form.name.trim(),
      desiredTitles: splitList(titles),
      locations: splitList(locations),
      skills: form.skills.filter((skill) => skill.name.trim() !== ""),
      languages: form.languages.filter((language) => language.code.trim() !== ""),
      entries: form.entries.filter((entry) => entry.title.trim() !== ""),
    };
    try {
      if (isNew) {
        const { data } = await api.createProfile(body);
        navigate(`/perfis/${data.id}`, { replace: true });
      } else if (id) {
        const { data } = await api.updateProfile(id, body);
        setForm(data);
        setSaved(true);
      }
    } catch (err) {
      setError(errorMessage(err));
      if (err && typeof err === "object" && "errors" in err && Array.isArray(err.errors)) {
        setDetails(err.errors as string[]);
      }
    }
  };

  const remove = async () => {
    if (!id || isNew || !window.confirm(t("common.confirmDelete"))) {
      return;
    }
    try {
      await api.deleteProfile(id);
      navigate("/perfis", { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  if (loading) {
    return <Loading />;
  }
  return (
    <form className="page" onSubmit={(event) => void submit(event)}>
      <div className="page-header">
        <h1>{isNew ? t("profiles.new") : form.name}</h1>
        <div className="row-actions">
          {!isNew && (
            <Button variant="danger" onClick={() => void remove()}>
              {t("common.delete")}
            </Button>
          )}
          <Button type="submit" variant="primary">
            {t("common.save")}
          </Button>
        </div>
      </div>
      <p className="muted">{t("profiles.truth")}</p>
      {error && (
        <Notice tone="error" onClose={() => setError(null)}>
          {error}
          {details.length > 0 && (
            <ul>
              {details.map((detail) => (
                <li key={detail}>{detail}</li>
              ))}
            </ul>
          )}
        </Notice>
      )}
      {saved && <Notice tone="success">{t("common.saved")}</Notice>}

      <Card>
        <div className="form-grid">
          <Field label={t("profiles.name")}>
            <input value={form.name} onChange={(e) => patch({ name: e.target.value })} required maxLength={120} />
          </Field>
          <Field label={t("profiles.seniority")}>
            <select value={form.seniority} onChange={(e) => patch({ seniority: e.target.value as ProfileInput["seniority"] })}>
              {SENIORITIES.map((s) => (
                <option key={s} value={s}>
                  {label("seniority", s)}
                </option>
              ))}
            </select>
          </Field>
          <Field label={t("profiles.minMatch")}>
            <input type="number" min={0} max={100} value={form.minMatchScore} onChange={(e) => patch({ minMatchScore: Number(e.target.value) })} />
          </Field>
          <Field label={t("profiles.preferredLanguage")}>
            <select value={form.preferredLanguage} onChange={(e) => patch({ preferredLanguage: e.target.value === "en" ? "en" : "pt-BR" })}>
              <option value="pt-BR">Português</option>
              <option value="en">English</option>
            </select>
          </Field>
          <Field label={t("profiles.objective")} wide>
            <textarea rows={3} maxLength={4000} value={form.objective ?? ""} onChange={(e) => patch({ objective: blank(e.target.value) })} />
          </Field>
          <Field label={t("profiles.titles")} wide>
            <input value={titles} onChange={(e) => setTitles(e.target.value)} />
          </Field>
          <Field label={t("profiles.locations")} wide>
            <input value={locations} onChange={(e) => setLocations(e.target.value)} />
          </Field>
          <fieldset className="field field-wide">
            <legend className="field-label">{t("profiles.workModes")}</legend>
            <div className="checks">
              {WORK_MODES.map((mode) => (
                <label key={mode} className="check">
                  <input type="checkbox" checked={form.workModes.includes(mode)} onChange={() => toggleMode(mode)} />
                  {label("workMode", mode)}
                </label>
              ))}
            </div>
          </fieldset>
          <Field label={t("profiles.salary")}>
            <input
              type="number"
              min={0}
              step="0.01"
              value={form.salaryExpectation ?? ""}
              onChange={(e) => patch({ salaryExpectation: e.target.value === "" ? null : Number(e.target.value) })}
            />
          </Field>
          <Field label={t("profiles.currency")}>
            <input value={form.salaryCurrency ?? ""} maxLength={3} onChange={(e) => patch({ salaryCurrency: blank(e.target.value.toUpperCase()) })} />
          </Field>
          <Field label={t("profiles.availability")} wide>
            <input value={form.availability ?? ""} maxLength={500} onChange={(e) => patch({ availability: blank(e.target.value) })} />
          </Field>
          <Field label={t("profiles.restrictions")} wide>
            <input value={form.restrictions ?? ""} maxLength={2000} onChange={(e) => patch({ restrictions: blank(e.target.value) })} />
          </Field>
        </div>
      </Card>

      <Card
        title={t("profiles.skills")}
        actions={<Button onClick={() => patch({ skills: [...form.skills, { name: "", level: 3, years: null }] })}>{t("common.add")}</Button>}
      >
        <table className="table">
          <thead>
            <tr>
              <th>{t("profiles.skillName")}</th>
              <th>{t("profiles.level")}</th>
              <th>{t("profiles.years")}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {form.skills.map((skill, index) => (
              <tr key={index}>
                <td>
                  <input aria-label={t("profiles.skillName")} value={skill.name} maxLength={80} onChange={(e) => setSkill(index, { name: e.target.value })} />
                </td>
                <td>
                  <input aria-label={t("profiles.level")} type="number" min={1} max={5} value={skill.level} onChange={(e) => setSkill(index, { level: Number(e.target.value) })} />
                </td>
                <td>
                  <input
                    aria-label={t("profiles.years")}
                    type="number"
                    min={0}
                    max={60}
                    step="0.5"
                    value={skill.years ?? ""}
                    onChange={(e) => setSkill(index, { years: e.target.value === "" ? null : Number(e.target.value) })}
                  />
                </td>
                <td>
                  <Button variant="ghost" onClick={() => patch({ skills: form.skills.filter((_, i) => i !== index) })}>
                    {t("common.remove")}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <Card
        title={t("profiles.languages")}
        actions={<Button onClick={() => patch({ languages: [...form.languages, { code: "", level: "B1" }] })}>{t("common.add")}</Button>}
      >
        <table className="table">
          <tbody>
            {form.languages.map((language, index) => (
              <tr key={index}>
                <td>
                  <input
                    aria-label={t("profiles.languageCode")}
                    placeholder={t("profiles.languageCode")}
                    value={language.code}
                    maxLength={2}
                    onChange={(e) => setLanguage(index, { code: e.target.value.toLowerCase() })}
                  />
                </td>
                <td>
                  <select value={language.level} onChange={(e) => setLanguage(index, { level: e.target.value as Language["level"] })}>
                    {LANGUAGE_LEVELS.map((level) => (
                      <option key={level} value={level}>
                        {level}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <Button variant="ghost" onClick={() => patch({ languages: form.languages.filter((_, i) => i !== index) })}>
                    {t("common.remove")}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <Card
        title={t("profiles.entries")}
        actions={
          <Button
            onClick={() =>
              patch({
                entries: [...form.entries, { kind: "PROJECT", title: "", organization: null, startDate: null, endDate: null, description: null, url: null }],
              })
            }
          >
            {t("common.add")}
          </Button>
        }
      >
        {form.entries.map((entry, index) => (
          <div className="form-grid entry" key={index}>
            <Field label={t("profiles.entryKind")}>
              <select value={entry.kind} onChange={(e) => setEntry(index, { kind: e.target.value as Entry["kind"] })}>
                {ENTRY_KINDS.map((kind) => (
                  <option key={kind} value={kind}>
                    {label("entryKind", kind)}
                  </option>
                ))}
              </select>
            </Field>
            <Field label={t("profiles.entryTitle")}>
              <input value={entry.title} maxLength={200} onChange={(e) => setEntry(index, { title: e.target.value })} />
            </Field>
            <Field label={t("profiles.organization")}>
              <input value={entry.organization ?? ""} maxLength={200} onChange={(e) => setEntry(index, { organization: blank(e.target.value) })} />
            </Field>
            <Field label={t("profiles.start")}>
              <input type="date" value={entry.startDate ?? ""} onChange={(e) => setEntry(index, { startDate: e.target.value || null })} />
            </Field>
            <Field label={t("profiles.end")}>
              <input type="date" value={entry.endDate ?? ""} onChange={(e) => setEntry(index, { endDate: e.target.value || null })} />
            </Field>
            <Field label={t("profiles.link")}>
              <input type="url" value={entry.url ?? ""} maxLength={2048} onChange={(e) => setEntry(index, { url: blank(e.target.value) })} />
            </Field>
            <Field label={t("jobs.description")} wide>
              <textarea rows={2} maxLength={4000} value={entry.description ?? ""} onChange={(e) => setEntry(index, { description: blank(e.target.value) })} />
            </Field>
            <div className="field">
              <Button variant="ghost" onClick={() => patch({ entries: form.entries.filter((_, i) => i !== index) })}>
                {t("common.remove")}
              </Button>
            </div>
          </div>
        ))}
      </Card>
    </form>
  );
}
