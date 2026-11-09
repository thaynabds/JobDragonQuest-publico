import type { ProfileInput, ProfileOptions } from "../api/types";
import type { DocxBlock } from "./docx";

export interface ResumeItem {
  key: string;
  text: string;
  detail?: string;
  visible: boolean;
}

export interface ResumeGroup {
  title: string;
  items: ResumeItem[];
}

type Field = NonNullable<ProfileOptions["resumeHidden"]>[number];
type Section = NonNullable<ProfileOptions["resumeVisible"]>[number];
type Label = (key: string) => string;

const WORK_KINDS = ["EXPERIENCE", "ACADEMIC_EXPERIENCE"];

/** Todos os dados do formulário como itens do currículo, cada um com a sua escolha de aparecer ou não. */
export function resumeGroups(form: ProfileInput, label: Label): ResumeGroup[] {
  const o: ProfileOptions = form.options ?? {};
  const hidden = new Set<string>(o.resumeHidden ?? []);
  const on = new Set<string>(o.resumeVisible ?? []);
  const field = (id: Field, text: string | null | undefined): ResumeItem[] => (text ? [{ key: `field:${id}`, text, visible: !hidden.has(id) }] : []);
  const contact = (id: Field, text: string | null | undefined): ResumeItem[] => (text ? [{ key: `contact:${id}`, text, visible: on.has("CONTACT") && !hidden.has(id) }] : []);
  const section = (id: Section, text: string | null | undefined): ResumeItem[] => (text ? [{ key: `section:${id}`, text: `${label(`options.section.${id}`)}: ${text}`, visible: on.has(id) }] : []);
  const phone = (value?: string | null) => (value ? [o.phoneCountryCode, value].filter(Boolean).join(" ") : null);
  const period = (start: string | null, end: string | null, current: boolean | null) =>
    [start?.slice(0, 7), current ? label("resume.current") : end?.slice(0, 7)].filter(Boolean).join(" – ");
  const entries = (match: (kind: string) => boolean) =>
    form.entries.flatMap((entry, index) =>
      entry.title.trim() !== "" && match(entry.kind)
        ? [{
            key: `entry:${index}`,
            text: [entry.title, entry.organization, period(entry.startDate, entry.endDate, entry.current)].filter(Boolean).join(" · "),
            detail: [entry.description, entry.url, ...(entry.extraUrls ?? [])].filter(Boolean).join("\n") || undefined,
            visible: entry.showInResume !== false,
          }]
        : [],
    );
  const location = [form.city, form.stateRegion, form.country].filter(Boolean).join(", ");
  const diversity = [...(o.diversity ?? []).map((item) => label(`options.diversity.${item}`)), ...(o.diversityOther ?? [])].filter(Boolean).join(", ");
  const groups: ResumeGroup[] = [
    { title: label("resume.builder.identity"), items: [...field("HEADLINE", form.headline), ...field("CURRENT_TITLE", form.currentTitle), ...field("LOCATION", location)] },
    { title: label("resume.builder.contact"), items: [...contact("PHONE", phone(o.phone)), ...contact("SECONDARY_PHONE", phone(o.secondaryPhone)), ...contact("EMAIL", o.contactEmail)] },
    {
      title: label("resume.builder.links"),
      items: [
        ...field("LINKEDIN", form.linkedinUrl),
        ...field("GITHUB", form.githubUrl),
        ...field("PORTFOLIO", form.portfolioUrl),
        ...field("WEBSITE", form.websiteUrl),
        ...(o.extraLinks ?? []).flatMap((link, index) => (link.url ? [{ key: `link:${index}`, text: `${link.label}: ${link.url}`, visible: link.showInResume !== false }] : [])),
      ],
    },
    { title: label("resume.summary"), items: field("SUMMARY", form.summary) },
    {
      title: label("resume.skills"),
      items: form.skills.flatMap((skill, index) => (skill.name.trim() !== "" ? [{ key: `skill:${index}`, text: skill.name, visible: skill.showInResume !== false }] : [])),
    },
    { title: label("resume.experience"), items: entries((kind) => WORK_KINDS.includes(kind)) },
    { title: label("resume.education"), items: entries((kind) => kind === "EDUCATION") },
    { title: label("resume.other"), items: entries((kind) => !WORK_KINDS.includes(kind) && kind !== "EDUCATION") },
    {
      title: label("resume.languages"),
      items: form.languages.flatMap((language, index) => (language.name.trim() !== "" ? [{ key: `lang:${index}`, text: `${language.name} (${language.level})`, visible: language.showInResume !== false }] : [])),
    },
    {
      title: label("resume.builder.more"),
      items: [
        ...section("SOFT_SKILLS", o.softSkills?.join(", ")),
        ...section("DIVERSITY", diversity),
        ...section("WORK_PREFERENCES", o.workload),
        ...section("SALARY", [o.salaryMin, o.salaryMax].filter((v) => v != null).join(" - ")),
        ...section("SOCIAL_NAME", o.socialName),
        ...section("GENDER", o.genderIdentity),
        ...section("BIRTH_DATE", o.birthDate),
        ...section("GPA", o.gpa),
        ...section("NATIONALITY", o.nationalities?.join(", ")),
        ...section("POSTAL_CODE", o.postalCode),
        ...section("MILITARY", o.military ? label(`options.militaryValue.${o.military}`) : null),
      ],
    },
  ];
  return groups.filter((group) => group.items.length > 0);
}

const flip = <T,>(values: T[] | null | undefined, value: T, present: boolean): T[] => {
  const rest = (values ?? []).filter((item) => item !== value);
  return present ? [...rest, value] : rest;
};

/** Aplica a escolha de mostrar ou esconder um item e devolve as mudanças do formulário. */
export function setItemVisible(form: ProfileInput, key: string, visible: boolean): Partial<ProfileInput> {
  const [kind, raw] = key.split(":");
  const index = Number(raw);
  const o: ProfileOptions = form.options ?? {};
  switch (kind) {
    case "field":
      return { options: { ...o, resumeHidden: flip(o.resumeHidden, raw as Field, !visible) } };
    case "contact":
      return { options: { ...o, resumeHidden: flip(o.resumeHidden, raw as Field, !visible), resumeVisible: visible ? flip(o.resumeVisible, "CONTACT" as Section, true) : o.resumeVisible } };
    case "section":
      return { options: { ...o, resumeVisible: flip(o.resumeVisible, raw as Section, visible) } };
    case "link":
      return { options: { ...o, extraLinks: (o.extraLinks ?? []).map((link, i) => (i === index ? { ...link, showInResume: visible } : link)) } };
    case "skill":
      return { skills: form.skills.map((skill, i) => (i === index ? { ...skill, showInResume: visible } : skill)) };
    case "lang":
      return { languages: form.languages.map((language, i) => (i === index ? { ...language, showInResume: visible } : language)) };
    case "entry":
      return { entries: form.entries.map((entry, i) => (i === index ? { ...entry, showInResume: visible } : entry)) };
    default:
      return {};
  }
}

/** Só o que está marcado: é o mesmo conteúdo da prévia e do arquivo Word. */
export function visibleBlocks(name: string, groups: ResumeGroup[]): DocxBlock[] {
  const blocks: DocxBlock[] = name ? [{ title: true, lines: [{ text: name }] }] : [];
  for (const group of groups) {
    const lines = group.items.filter((item) => item.visible).map(({ text, detail }) => ({ text, detail }));
    if (lines.length > 0) blocks.push({ heading: group.title, lines });
  }
  return blocks;
}
