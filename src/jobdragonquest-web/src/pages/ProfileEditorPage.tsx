import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import { useNavigate, useParams } from "react-router";

import { api } from "../api/endpoints";
import {
  DEGREES,
  EMPLOYMENT_TYPES,
  ENTRY_KINDS,
  ENTRY_STATUSES,
  LANGUAGE_LEVELS,
  SALARY_PERIODS,
  SENIORITIES,
  TRAVEL_AVAILABILITIES,
  WORK_MODES,
  type Answer,
  type EmploymentType,
  type Entry,
  type LanguageInput,
  type Profile,
  type ProfileInput,
  type Skill,
  type WorkMode,
} from "../api/types";
import { Button, Card, Field, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { isLocale, LOCALE_NAMES, LOCALES, type Locale, type MessageKey } from "../i18n/messages";
import { countryCode, countryName, countryOptions, languageName, languageOptions } from "../utils/places";
import { splitList } from "../utils/safe";

/** Campos de lista editados como texto separado por vírgula. */
const LIST_FIELDS = ["desiredTitles", "locations", "industries", "includeKeywords", "excludeKeywords", "excludedCompanies"] as const;
type ListField = (typeof LIST_FIELDS)[number];

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
  languages: [],
  entries: [],
  headline: null,
  summary: null,
  currentTitle: null,
  city: null,
  stateRegion: null,
  country: null,
  requiresSponsorship: null,
  willingToRelocate: null,
  travelAvailability: null,
  noticePeriodDays: null,
  availableFrom: null,
  currentSalary: null,
  salaryPeriod: "MONTHLY",
  linkedinUrl: null,
  githubUrl: null,
  portfolioUrl: null,
  websiteUrl: null,
  coverLetter: null,
  disability: null,
  disabilityDetails: null,
  workAuthorization: [],
  employmentTypes: [],
  excludedCompanies: [],
  includeKeywords: [],
  excludeKeywords: [],
  industries: [],
  answers: [],
};

const NEW_ENTRY: Entry = {
  kind: "EXPERIENCE",
  title: "",
  organization: null,
  startDate: null,
  endDate: null,
  description: null,
  url: null,
  location: null,
  employmentType: null,
  current: null,
  degree: null,
  fieldOfStudy: null,
  status: null,
  credentialId: null,
  expirationDate: null,
  hours: null,
  skills: null,
};

const blank = (value: string) => (value.trim() === "" ? null : value);
const number = (value: string) => (value === "" ? null : Number(value));

/** Converte o perfil salvo para a edição: idiomas viram nomes por extenso no idioma do painel. */
export function toInput(profile: Profile, locale: string): ProfileInput {
  const { id: _id, createdAt: _c, updatedAt: _u, skills, languages, ...rest } = profile;
  return {
    ...rest,
    skills: skills.map(({ totalYears: _t, ...skill }) => skill),
    languages: languages.map((language) => ({ name: languageName(language.code, locale), level: language.level })),
  };
}

function Section({ title, children, actions }: { title: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <Card title={title} actions={actions}>
      <div className="form-grid">{children}</div>
    </Card>
  );
}

export function ProfileEditorPage() {
  const { id } = useParams();
  const isNew = !id || id === "novo";
  const navigate = useNavigate();
  const { t, label, errorMessage, locale } = useI18n();
  const [form, setForm] = useState<ProfileInput>(EMPTY);
  const [lists, setLists] = useState<Record<ListField, string>>(() => Object.fromEntries(LIST_FIELDS.map((f) => [f, ""])) as Record<ListField, string>);
  const [countryText, setCountryText] = useState("");
  const [authorizationText, setAuthorizationText] = useState("");
  const [loading, setLoading] = useState(!isNew);
  const [error, setError] = useState<string | null>(null);
  const [details, setDetails] = useState<string[]>([]);
  const [saved, setSaved] = useState(false);
  const languageList = useMemo(() => languageOptions(locale), [locale]);
  const countryList = useMemo(() => countryOptions(locale), [locale]);

  const load = (profile: Profile) => {
    const input = toInput(profile, locale);
    setForm(input);
    setLists(Object.fromEntries(LIST_FIELDS.map((field) => [field, input[field].join(", ")])) as Record<ListField, string>);
    setCountryText(input.country ? countryName(input.country, locale) : "");
    setAuthorizationText(input.workAuthorization.map((code) => countryName(code, locale)).join(", "));
  };

  useEffect(() => {
    if (isNew || !id) {
      return;
    }
    api
      .profile(id)
      .then(({ data }) => load(data))
      .catch((err: unknown) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, isNew]);

  const patch = (change: Partial<ProfileInput>) => setForm((current) => ({ ...current, ...change }));
  const setSkill = (index: number, change: Partial<Skill>) =>
    patch({ skills: form.skills.map((skill, i) => (i === index ? { ...skill, ...change } : skill)) });
  const setLanguage = (index: number, change: Partial<LanguageInput>) =>
    patch({ languages: form.languages.map((language, i) => (i === index ? { ...language, ...change } : language)) });
  const setEntry = (index: number, change: Partial<Entry>) =>
    patch({ entries: form.entries.map((entry, i) => (i === index ? { ...entry, ...change } : entry)) });
  const setAnswer = (index: number, change: Partial<Answer>) =>
    patch({ answers: form.answers.map((answer, i) => (i === index ? { ...answer, ...change } : answer)) });
  const toggle = <T,>(values: T[], value: T) => (values.includes(value) ? values.filter((v) => v !== value) : [...values, value]);
  const yesNo = (value: boolean | null) => (value === null ? "" : value ? "yes" : "no");
  const fromYesNo = (value: string) => (value === "" ? null : value === "yes");

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setDetails([]);
    setSaved(false);
    const country = countryText.trim() === "" ? null : countryCode(countryText, locale);
    if (countryText.trim() !== "" && !country) {
      setError(t("profiles.unknownCountry", { value: countryText.trim() }));
      return;
    }
    const authorization = splitList(authorizationText).map((name) => ({ name, code: countryCode(name, locale) }));
    const unknown = authorization.find((item) => !item.code);
    if (unknown) {
      setError(t("profiles.unknownCountry", { value: unknown.name }));
      return;
    }
    const body: ProfileInput = {
      ...form,
      name: form.name.trim(),
      ...(Object.fromEntries(LIST_FIELDS.map((field) => [field, splitList(lists[field])])) as Record<ListField, string[]>),
      country,
      workAuthorization: [...new Set(authorization.map((item) => item.code as string))],
      skills: form.skills.filter((skill) => skill.name.trim() !== ""),
      languages: form.languages.filter((language) => language.name.trim() !== "").map((language) => ({ ...language, name: language.name.trim() })),
      entries: form.entries.filter((entry) => entry.title.trim() !== ""),
      answers: form.answers.filter((answer) => answer.question.trim() !== "" && answer.answer.trim() !== ""),
    };
    try {
      if (isNew) {
        const { data } = await api.createProfile(body);
        navigate(`/perfis/${data.id}`, { replace: true });
      } else if (id) {
        const { data } = await api.updateProfile(id, body);
        load(data);
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

  const listField = (field: ListField, key: MessageKey, hint?: MessageKey) => (
    <Field label={t(key)} hint={hint ? t(hint) : undefined} wide>
      <input value={lists[field]} onChange={(e) => setLists({ ...lists, [field]: e.target.value })} />
    </Field>
  );

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

      <datalist id="language-options">
        {languageList.map((name) => (
          <option key={name} value={name} />
        ))}
      </datalist>
      <datalist id="country-options">
        {countryList.map((name) => (
          <option key={name} value={name} />
        ))}
      </datalist>

      <Section title={t("profiles.section.identity")}>
        <Field label={t("profiles.name")} hint={t("profiles.nameHint")}>
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
        <Field label={t("profiles.headline")} hint={t("profiles.headlineHint")} wide>
          <input value={form.headline ?? ""} maxLength={220} onChange={(e) => patch({ headline: blank(e.target.value) })} />
        </Field>
        <Field label={t("profiles.currentTitle")}>
          <input value={form.currentTitle ?? ""} maxLength={160} onChange={(e) => patch({ currentTitle: blank(e.target.value) })} />
        </Field>
        <Field label={t("profiles.minMatch")}>
          <input type="number" min={0} max={100} value={form.minMatchScore} onChange={(e) => patch({ minMatchScore: Number(e.target.value) })} />
        </Field>
        <Field label={t("profiles.preferredLanguage")}>
          <select value={form.preferredLanguage} onChange={(e) => patch({ preferredLanguage: e.target.value })}>
            {(Object.keys(LOCALES) as Locale[]).map((code) => (
              <option key={code} value={code}>
                {LOCALE_NAMES[code]}
              </option>
            ))}
            {!isLocale(form.preferredLanguage) && <option value={form.preferredLanguage}>{form.preferredLanguage}</option>}
          </select>
        </Field>
        <Field label={t("profiles.summary")} hint={t("profiles.summaryHint")} wide>
          <textarea rows={4} maxLength={4000} value={form.summary ?? ""} onChange={(e) => patch({ summary: blank(e.target.value) })} />
        </Field>
        <Field label={t("profiles.objective")} wide>
          <textarea rows={2} maxLength={4000} value={form.objective ?? ""} onChange={(e) => patch({ objective: blank(e.target.value) })} />
        </Field>
      </Section>

      <Section title={t("profiles.section.location")}>
        <Field label={t("profiles.city")}>
          <input value={form.city ?? ""} maxLength={120} onChange={(e) => patch({ city: blank(e.target.value) })} />
        </Field>
        <Field label={t("profiles.stateRegion")}>
          <input value={form.stateRegion ?? ""} maxLength={120} onChange={(e) => patch({ stateRegion: blank(e.target.value) })} />
        </Field>
        <Field label={t("profiles.country")}>
          <input list="country-options" value={countryText} onChange={(e) => setCountryText(e.target.value)} />
        </Field>
        <Field label={t("profiles.workAuthorization")} hint={t("profiles.workAuthorizationHint")} wide>
          <input value={authorizationText} onChange={(e) => setAuthorizationText(e.target.value)} />
        </Field>
        <Field label={t("profiles.requiresSponsorship")}>
          <select value={yesNo(form.requiresSponsorship)} onChange={(e) => patch({ requiresSponsorship: fromYesNo(e.target.value) })}>
            <option value="">{t("common.none")}</option>
            <option value="yes">{t("common.yes")}</option>
            <option value="no">{t("common.no")}</option>
          </select>
        </Field>
        <Field label={t("profiles.willingToRelocate")}>
          <select value={yesNo(form.willingToRelocate)} onChange={(e) => patch({ willingToRelocate: fromYesNo(e.target.value) })}>
            <option value="">{t("common.none")}</option>
            <option value="yes">{t("common.yes")}</option>
            <option value="no">{t("common.no")}</option>
          </select>
        </Field>
        <Field label={t("profiles.travel")}>
          <select value={form.travelAvailability ?? ""} onChange={(e) => patch({ travelAvailability: (e.target.value || null) as ProfileInput["travelAvailability"] })}>
            <option value="">{t("common.none")}</option>
            {TRAVEL_AVAILABILITIES.map((value) => (
              <option key={value} value={value}>
                {label("travel", value)}
              </option>
            ))}
          </select>
        </Field>
        <Field label={t("profiles.availableFrom")}>
          <input type="date" value={form.availableFrom ?? ""} onChange={(e) => patch({ availableFrom: e.target.value || null })} />
        </Field>
        <Field label={t("profiles.noticePeriodDays")}>
          <input type="number" min={0} max={365} value={form.noticePeriodDays ?? ""} onChange={(e) => patch({ noticePeriodDays: number(e.target.value) })} />
        </Field>
        <Field label={t("profiles.availability")} wide>
          <input value={form.availability ?? ""} maxLength={500} onChange={(e) => patch({ availability: blank(e.target.value) })} />
        </Field>
        <fieldset className="field field-wide">
          <legend className="field-label">{t("profiles.workModes")}</legend>
          <div className="checks">
            {WORK_MODES.map((mode: WorkMode) => (
              <label key={mode} className="check">
                <input type="checkbox" checked={form.workModes.includes(mode)} onChange={() => patch({ workModes: toggle(form.workModes, mode) })} />
                {label("workMode", mode)}
              </label>
            ))}
          </div>
        </fieldset>
        <fieldset className="field field-wide">
          <legend className="field-label">{t("profiles.employmentTypes")}</legend>
          <div className="checks">
            {EMPLOYMENT_TYPES.map((type: EmploymentType) => (
              <label key={type} className="check">
                <input type="checkbox" checked={form.employmentTypes.includes(type)} onChange={() => patch({ employmentTypes: toggle(form.employmentTypes, type) })} />
                {label("employmentType", type)}
              </label>
            ))}
          </div>
        </fieldset>
      </Section>

      <Section title={t("profiles.section.salary")}>
        <Field label={t("profiles.salary")}>
          <input type="number" min={0} step="0.01" value={form.salaryExpectation ?? ""} onChange={(e) => patch({ salaryExpectation: number(e.target.value) })} />
        </Field>
        <Field label={t("profiles.salaryPeriod")}>
          <select value={form.salaryPeriod ?? ""} onChange={(e) => patch({ salaryPeriod: (e.target.value || null) as ProfileInput["salaryPeriod"] })}>
            <option value="">{t("common.none")}</option>
            {SALARY_PERIODS.map((value) => (
              <option key={value} value={value}>
                {label("salaryPeriod", value)}
              </option>
            ))}
          </select>
        </Field>
        <Field label={t("profiles.currency")}>
          <input value={form.salaryCurrency ?? ""} maxLength={3} onChange={(e) => patch({ salaryCurrency: blank(e.target.value.toUpperCase()) })} />
        </Field>
        <Field label={t("profiles.currentSalary")} hint={t("common.optional")}>
          <input type="number" min={0} step="0.01" value={form.currentSalary ?? ""} onChange={(e) => patch({ currentSalary: number(e.target.value) })} />
        </Field>
      </Section>

      <Section title={t("profiles.section.links")}>
        {(["linkedinUrl", "githubUrl", "portfolioUrl", "websiteUrl"] as const).map((field) => (
          <Field key={field} label={t(`profiles.${field}` as MessageKey)}>
            <input type="url" value={form[field] ?? ""} maxLength={2048} placeholder="https://" onChange={(e) => patch({ [field]: blank(e.target.value) })} />
          </Field>
        ))}
      </Section>

      <Section title={t("profiles.section.search")}>
        {listField("desiredTitles", "profiles.titles")}
        {listField("locations", "profiles.locations")}
        {listField("industries", "profiles.industries")}
        {listField("includeKeywords", "profiles.includeKeywords", "profiles.keywordsHint")}
        {listField("excludeKeywords", "profiles.excludeKeywords", "profiles.keywordsHint")}
        {listField("excludedCompanies", "profiles.excludedCompanies")}
        <Field label={t("profiles.restrictions")} wide>
          <input value={form.restrictions ?? ""} maxLength={2000} onChange={(e) => patch({ restrictions: blank(e.target.value) })} />
        </Field>
      </Section>

      <Card
        title={t("profiles.skills")}
        actions={<Button onClick={() => patch({ skills: [...form.skills, { name: "", level: 3, years: null, months: null, days: null }] })}>{t("common.add")}</Button>}
      >
        <p className="muted">{t("profiles.skillTimeHint")}</p>
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th>{t("profiles.skillName")}</th>
                <th>{t("profiles.level")}</th>
                <th>{t("profiles.years")}</th>
                <th>{t("profiles.months")}</th>
                <th>{t("profiles.days")}</th>
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
                    <input aria-label={t("profiles.years")} type="number" min={0} max={60} value={skill.years ?? ""} onChange={(e) => setSkill(index, { years: number(e.target.value) })} />
                  </td>
                  <td>
                    <input aria-label={t("profiles.months")} type="number" min={0} max={11} value={skill.months ?? ""} onChange={(e) => setSkill(index, { months: number(e.target.value) })} />
                  </td>
                  <td>
                    <input aria-label={t("profiles.days")} type="number" min={0} max={30} value={skill.days ?? ""} onChange={(e) => setSkill(index, { days: number(e.target.value) })} />
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
        </div>
      </Card>

      <Card title={t("profiles.languages")} actions={<Button onClick={() => patch({ languages: [...form.languages, { name: "", level: "B1" }] })}>{t("common.add")}</Button>}>
        <p className="muted">{t("profiles.languageHint")}</p>
        <table className="table">
          <tbody>
            {form.languages.map((language, index) => (
              <tr key={index}>
                <td>
                  <input
                    aria-label={t("profiles.languageName")}
                    placeholder={t("profiles.languageName")}
                    list="language-options"
                    value={language.name}
                    maxLength={60}
                    onChange={(e) => setLanguage(index, { name: e.target.value })}
                  />
                </td>
                <td>
                  <select aria-label={t("profiles.languageLevel")} value={language.level} onChange={(e) => setLanguage(index, { level: e.target.value as LanguageInput["level"] })}>
                    {LANGUAGE_LEVELS.map((level) => (
                      <option key={level} value={level}>
                        {label("languageLevel", level)}
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

      <Card title={t("profiles.entries")} actions={<Button onClick={() => patch({ entries: [...form.entries, { ...NEW_ENTRY }] })}>{t("common.add")}</Button>}>
        {form.entries.map((entry, index) => {
          const job = entry.kind === "EXPERIENCE" || entry.kind === "VOLUNTEER" || entry.kind === "ACADEMIC_EXPERIENCE";
          const study = entry.kind === "EDUCATION" || entry.kind === "COURSE";
          return (
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
              {job && (
                <>
                  <Field label={t("profiles.employmentType")}>
                    <select value={entry.employmentType ?? ""} onChange={(e) => setEntry(index, { employmentType: (e.target.value || null) as Entry["employmentType"] })}>
                      <option value="">{t("common.none")}</option>
                      {EMPLOYMENT_TYPES.map((type) => (
                        <option key={type} value={type}>
                          {label("employmentType", type)}
                        </option>
                      ))}
                    </select>
                  </Field>
                  <Field label={t("profiles.entryLocation")}>
                    <input value={entry.location ?? ""} maxLength={160} onChange={(e) => setEntry(index, { location: blank(e.target.value) })} />
                  </Field>
                </>
              )}
              {study && (
                <>
                  {entry.kind === "EDUCATION" && (
                    <Field label={t("profiles.degree")}>
                      <select value={entry.degree ?? ""} onChange={(e) => setEntry(index, { degree: (e.target.value || null) as Entry["degree"] })}>
                        <option value="">{t("common.none")}</option>
                        {DEGREES.map((degree) => (
                          <option key={degree} value={degree}>
                            {label("degree", degree)}
                          </option>
                        ))}
                      </select>
                    </Field>
                  )}
                  <Field label={t("profiles.fieldOfStudy")}>
                    <input value={entry.fieldOfStudy ?? ""} maxLength={160} onChange={(e) => setEntry(index, { fieldOfStudy: blank(e.target.value) })} />
                  </Field>
                  <Field label={t("profiles.entryStatus")}>
                    <select value={entry.status ?? ""} onChange={(e) => setEntry(index, { status: (e.target.value || null) as Entry["status"] })}>
                      <option value="">{t("common.none")}</option>
                      {ENTRY_STATUSES.map((status) => (
                        <option key={status} value={status}>
                          {label("entryStatus", status)}
                        </option>
                      ))}
                    </select>
                  </Field>
                  {entry.kind === "COURSE" && (
                    <Field label={t("profiles.hours")}>
                      <input type="number" min={0} max={100000} value={entry.hours ?? ""} onChange={(e) => setEntry(index, { hours: number(e.target.value) })} />
                    </Field>
                  )}
                </>
              )}
              {entry.kind === "CERTIFICATION" && (
                <>
                  <Field label={t("profiles.credentialId")}>
                    <input value={entry.credentialId ?? ""} maxLength={120} onChange={(e) => setEntry(index, { credentialId: blank(e.target.value) })} />
                  </Field>
                  <Field label={t("profiles.expirationDate")}>
                    <input type="date" value={entry.expirationDate ?? ""} onChange={(e) => setEntry(index, { expirationDate: e.target.value || null })} />
                  </Field>
                </>
              )}
              <Field label={t("profiles.start")}>
                <input type="date" value={entry.startDate ?? ""} onChange={(e) => setEntry(index, { startDate: e.target.value || null })} />
              </Field>
              <Field label={t("profiles.end")}>
                <input type="date" value={entry.endDate ?? ""} disabled={entry.current === true} onChange={(e) => setEntry(index, { endDate: e.target.value || null })} />
              </Field>
              {(job || study) && (
                <label className="check field">
                  <input type="checkbox" checked={entry.current === true} onChange={(e) => setEntry(index, { current: e.target.checked, endDate: e.target.checked ? null : entry.endDate })} />
                  {t(study ? "profiles.currentStudy" : "profiles.currentJob")}
                </label>
              )}
              <Field label={t("profiles.entrySkills")} hint={t("profiles.entrySkillsHint")}>
                <input value={entry.skills ?? ""} maxLength={500} onChange={(e) => setEntry(index, { skills: blank(e.target.value) })} />
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
          );
        })}
      </Card>

      <Section title={t("profiles.coverLetter")}>
        <Field label={t("profiles.coverLetter")} hint={t("profiles.coverLetterHint")} wide>
          <textarea rows={8} maxLength={8000} value={form.coverLetter ?? ""} onChange={(e) => patch({ coverLetter: blank(e.target.value) })} />
        </Field>
      </Section>

      <Card title={t("profiles.answers")} actions={<Button onClick={() => patch({ answers: [...form.answers, { question: "", answer: "" }] })}>{t("common.add")}</Button>}>
        <p className="muted">{t("profiles.answersHint")}</p>
        {form.answers.map((answer, index) => (
          <div className="form-grid entry" key={index}>
            <Field label={t("profiles.question")} wide>
              <input value={answer.question} maxLength={500} onChange={(e) => setAnswer(index, { question: e.target.value })} />
            </Field>
            <Field label={t("profiles.answer")} wide>
              <textarea rows={3} maxLength={4000} value={answer.answer} onChange={(e) => setAnswer(index, { answer: e.target.value })} />
            </Field>
            <div className="field">
              <Button variant="ghost" onClick={() => patch({ answers: form.answers.filter((_, i) => i !== index) })}>
                {t("common.remove")}
              </Button>
            </div>
          </div>
        ))}
      </Card>

      <Section title={t("profiles.section.sensitive")}>
        <p className="muted field-wide">{t("profiles.sensitiveHint")}</p>
        <Field label={t("profiles.disability")}>
          <select value={yesNo(form.disability)} onChange={(e) => patch({ disability: fromYesNo(e.target.value), disabilityDetails: e.target.value === "yes" ? form.disabilityDetails : null })}>
            <option value="">{t("profiles.preferNotToSay")}</option>
            <option value="yes">{t("common.yes")}</option>
            <option value="no">{t("common.no")}</option>
          </select>
        </Field>
        {form.disability === true && (
          <Field label={t("profiles.disabilityDetails")} wide>
            <input value={form.disabilityDetails ?? ""} maxLength={500} onChange={(e) => patch({ disabilityDetails: blank(e.target.value) })} />
          </Field>
        )}
      </Section>
    </form>
  );
}
