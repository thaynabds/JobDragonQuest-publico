import type { ReactNode } from "react";

import { CONTACT_DAYS, DIVERSITY_OPTIONS, RESUME_SECTIONS, type ProfileOptions, type Seniority } from "../api/types";
import { Button, Card, Field } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import type { MessageKey } from "../i18n/messages";
import { splitList } from "../utils/safe";

const SENIORITIES: Seniority[] = ["APPRENTICE", "INTERN", "TRAINEE", "ENTRY", "JUNIOR", "MID", "SENIOR", "SPECIALIST", "LEAD", "COORDINATOR", "MANAGER", "DIRECTOR", "EXECUTIVE"];
const TRANSLATION_LANGUAGES = ["en", "es"] as const;

const blank = (value: string) => (value.trim() === "" ? null : value);
const money = (value: string) => (value === "" ? null : Number(value));

/** Opções de busca, de candidatura e de currículo. O que é sensível só aparece no currículo se a seção for ligada. */
export function ProfileOptionsSection({ value, onChange }: { value: ProfileOptions | null | undefined; onChange: (options: ProfileOptions) => void }) {
  const { t, label } = useI18n();
  const o: ProfileOptions = value ?? {};
  const set = (change: Partial<ProfileOptions>) => onChange({ ...o, ...change });
  const k = (key: string) => t(key as MessageKey);

  const toggle = <T,>(list: T[] | null | undefined, item: T): T[] => {
    const current = list ?? [];
    return current.includes(item) ? current.filter((x) => x !== item) : [...current, item];
  };
  const text = (field: keyof ProfileOptions, key: string, max: number, type = "text") => (
    <Field label={k(key)} info={key === "options.socialName" ? t("help.options.socialName") : key === "options.gpa" ? t("help.options.gpa") : undefined}>
      <input type={type} maxLength={max} value={(o[field] as string | null | undefined) ?? ""} onChange={(e) => set({ [field]: blank(e.target.value) })} />
    </Field>
  );
  const number = (field: keyof ProfileOptions, key: string) => (
    <Field label={k(key)} info={key === "options.salaryFrom" ? t("help.options.salaryFrom") : undefined}>
      <input type="number" min={0} step="0.01" value={(o[field] as number | null | undefined) ?? ""} onChange={(e) => set({ [field]: money(e.target.value) })} />
    </Field>
  );
  const list = (field: keyof ProfileOptions, key: string) => (
    <Field label={k(key)} hint={t("options.commaHint")} wide>
      <input defaultValue={((o[field] as string[] | null | undefined) ?? []).join(", ")} onBlur={(e) => set({ [field]: splitList(e.target.value) })} />
    </Field>
  );
  const check = (field: keyof ProfileOptions, key: string) => (
    <label className="checkbox">
      <input type="checkbox" checked={o[field] === true} onChange={(e) => set({ [field]: e.target.checked })} /> {k(key)}
    </label>
  );
  const group = (title: string, children: ReactNode) => (
    <fieldset className="field-wide options-group">
      <legend>{k(title)}</legend>
      <div className="form-grid">{children}</div>
    </fieldset>
  );

  const links = o.extraLinks ?? [];
  const setLink = (index: number, change: Partial<{ label: string; url: string }>) =>
    set({ extraLinks: links.map((link, i) => (i === index ? { ...link, ...change } : link)) });
  const translations = o.translations ?? [];
  const translation = (language: string) => translations.find((x) => x.language === language);
  const setTranslation = (language: string, change: Partial<{ headline: string | null; summary: string | null; coverLetter: string | null }>) => {
    const others = translations.filter((x) => x.language !== language);
    set({ translations: [...others, { language, headline: null, summary: null, coverLetter: null, ...translation(language), ...change }] });
  };
  const hours = o.contactHours ?? [];
  const slot = (day: string) => hours.find((h) => h.day === day)?.slots[0];
  const setSlot = (day: string, change: Partial<{ start: string; end: string }>) => {
    const others = hours.filter((h) => h.day !== day);
    const merged = { start: "", end: "", ...slot(day), ...change };
    set({ contactHours: merged.start && merged.end ? [...others, { day, slots: [merged] }] : others });
  };

  return (
    <Card title={t("options.title")}>
      <p className="muted small">{t("options.intro")}</p>
      <div className="form-grid">
        {group("options.search", (
          <>
            <fieldset className="field-wide">
              <legend>{t("options.seniorities")}</legend>
              {SENIORITIES.map((s) => (
                <label className="checkbox" key={s}>
                  <input type="checkbox" checked={(o.targetSeniorities ?? []).includes(s)} onChange={() => set({ targetSeniorities: toggle(o.targetSeniorities, s) })} /> {label("seniority", s)}
                </label>
              ))}
            </fieldset>
            {list("timeZones", "options.timeZones")}
            {list("jobLanguages", "options.jobLanguages")}
            <Field label={t("options.radius")} info={t("help.options.radius")}>
              <input type="number" min={0} max={1000} value={o.locationRadiusKm ?? ""} onChange={(e) => set({ locationRadiusKm: e.target.value === "" ? null : Number(e.target.value) })} />
            </Field>
          </>
        ))}
        {group("options.application", (
          <>
            <Field label={t("options.coverLetterMode")}>
              <select value={o.coverLetterMode ?? ""} onChange={(e) => set({ coverLetterMode: (e.target.value || null) as ProfileOptions["coverLetterMode"] })}>
                <option value="">{t("common.none")}</option>
                <option value="GENERATED">{t("options.letterGenerated")}</option>
                <option value="OWN">{t("options.letterOwn")}</option>
              </select>
            </Field>
            <Field label={t("options.writingStyle")} info={t("help.options.writingStyle")} wide>
              <textarea rows={2} maxLength={1000} value={o.writingStyle ?? ""} onChange={(e) => set({ writingStyle: blank(e.target.value) })} />
            </Field>
            {text("phoneCountryCode", "options.phoneCountryCode", 8)}
            {text("phone", "options.phone", 30, "tel")}
            {check("phoneWhatsapp", "options.whatsapp")}
            {text("secondaryPhone", "options.secondaryPhone", 30, "tel")}
            {check("secondaryPhoneWhatsapp", "options.whatsapp")}
            {text("contactEmail", "options.contactEmail", 254, "email")}
            {check("contactPermission", "options.contactPermission")}
            {text("postalCode", "options.postalCode", 20)}
            {list("nationalities", "options.nationalities")}
            {text("workload", "options.workload", 40)}
            {check("transportAid", "options.transportAid")}
            {check("practicalExperience", "options.practicalExperience")}
          </>
        ))}
        {group("options.salary", (
          <>
            {number("expectedYearlySalary", "options.expectedYearly")}
            {number("currentHourlySalary", "options.currentHourly")}
            {number("expectedHourlySalary", "options.expectedHourly")}
            {number("salaryMin", "options.salaryMin")}
            {number("salaryMax", "options.salaryMax")}
            {number("salaryConsiderFrom", "options.salaryFrom")}
          </>
        ))}
        {group("options.personal", (
          <>
            {text("socialName", "options.socialName", 120)}
            {text("genderIdentity", "options.gender", 60)}
            {text("birthDate", "options.birthDate", 10, "date")}
            {check("over18", "options.over18")}
            {text("gpa", "options.gpa", 10)}
            <Field label={t("options.military")}>
              <select value={o.military ?? ""} onChange={(e) => set({ military: (e.target.value || null) as ProfileOptions["military"] })}>
                <option value="">{t("common.none")}</option>
                <option value="YES">{t("options.yes")}</option>
                <option value="NO">{t("options.no")}</option>
                <option value="PREFER_NOT_TO_SAY">{t("options.preferNot")}</option>
              </select>
            </Field>
            <fieldset className="field-wide">
              <legend>{t("options.diversity")}</legend>
              {DIVERSITY_OPTIONS.map((d) => (
                <label className="checkbox" key={d}>
                  <input type="checkbox" checked={(o.diversity ?? []).includes(d)} onChange={() => set({ diversity: toggle(o.diversity, d) })} /> {k(`options.diversity.${d}`)}
                </label>
              ))}
            </fieldset>
          </>
        ))}
        {group("options.resume", (
          <>
            {list("softSkills", "options.softSkills")}
            <fieldset className="field-wide">
              <legend>{t("options.contactHours")}</legend>
              {CONTACT_DAYS.map((day) => (
                <div className="form-row" key={day}>
                  <span className="contact-day">{k(`options.day.${day}`)}</span>
                  <input type="time" aria-label={`${k(`options.day.${day}`)}: ${t("options.from")}`} value={slot(day)?.start ?? ""} onChange={(e) => setSlot(day, { start: e.target.value })} />
                  <input type="time" aria-label={`${k(`options.day.${day}`)}: ${t("options.to")}`} value={slot(day)?.end ?? ""} onChange={(e) => setSlot(day, { end: e.target.value })} />
                </div>
              ))}
            </fieldset>
            <fieldset className="field-wide">
              <legend>{t("options.resumeVisible")}</legend>
              {RESUME_SECTIONS.map((section) => (
                <label className="checkbox" key={section}>
                  <input type="checkbox" checked={(o.resumeVisible ?? []).includes(section)} onChange={() => set({ resumeVisible: toggle(o.resumeVisible, section) })} /> {k(`options.section.${section}`)}
                </label>
              ))}
            </fieldset>
          </>
        ))}
        {group("options.links", (
          <>
            {links.map((link, index) => (
              <div className="form-grid entry field-wide" key={index}>
                <Field label={t("options.linkLabel")}>
                  <input required maxLength={60} value={link.label} placeholder={t("options.linkLabelPlaceholder")} onChange={(e) => setLink(index, { label: e.target.value })} />
                </Field>
                <Field label={t("options.linkUrl")}>
                  <input required type="url" maxLength={2048} value={link.url} placeholder="https://" onChange={(e) => setLink(index, { url: e.target.value })} />
                </Field>
                <div className="field">
                  <Button variant="ghost" onClick={() => set({ extraLinks: links.filter((_, i) => i !== index) })}>
                    {t("common.remove")}
                  </Button>
                </div>
              </div>
            ))}
            <div className="field">
              <Button disabled={links.length >= 20} onClick={() => set({ extraLinks: [...links, { label: "", url: "" }] })}>
                {t("options.addLink")}
              </Button>
            </div>
          </>
        ))}
        {group("options.languageDocs", (
          <>
            {check("documentsInJobLanguage", "options.documentsInJobLanguage")}
            {check("autoTranslate", "options.autoTranslate")}
            <p className="field-wide muted small">{t("options.autoTranslateHint")}</p>
            {TRANSLATION_LANGUAGES.map((language) => (
              <details className="field-wide" key={language}>
                <summary>{k(`options.translation.${language}`)}</summary>
                <div className="form-grid">
                  <Field label={t("options.trHeadline")} wide>
                    <input maxLength={220} value={translation(language)?.headline ?? ""} onChange={(e) => setTranslation(language, { headline: blank(e.target.value) })} />
                  </Field>
                  <Field label={t("options.trSummary")} wide>
                    <textarea rows={4} maxLength={4000} value={translation(language)?.summary ?? ""} onChange={(e) => setTranslation(language, { summary: blank(e.target.value) })} />
                  </Field>
                  <Field label={t("options.trLetter")} wide>
                    <textarea rows={6} maxLength={8000} value={translation(language)?.coverLetter ?? ""} onChange={(e) => setTranslation(language, { coverLetter: blank(e.target.value) })} />
                  </Field>
                </div>
              </details>
            ))}
          </>
        ))}
      </div>
    </Card>
  );
}
