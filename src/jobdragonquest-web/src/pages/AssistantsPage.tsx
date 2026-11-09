import { useState } from "react";

import { Button, Card, Field, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import type { MessageKey } from "../i18n/messages";

const COMMON = ["jobTitle", "years", "team", "scope", "industry", "location", "workMode"] as const;
const ASSISTANTS: { id: string; fields: readonly string[] }[] = [
  { id: "entrevista", fields: ["jobTitle"] },
  { id: "negociacao", fields: ["jobTitle", "offer"] },
  { id: "recem_formado", fields: ["course"] },
  { id: "personalidade", fields: [] },
  { id: "troca_carreira", fields: ["jobTitle", "targetTitle"] },
  { id: "curriculo", fields: ["jobTitle", "jobDescription"] },
  { id: "carta", fields: ["jobTitle", "jobDescription"] },
  { id: "salarios", fields: COMMON },
  { id: "caminhos", fields: COMMON },
  { id: "aprendizado", fields: [...COMMON, "targetTitle", "targetIndustry"] },
  { id: "mercado", fields: COMMON },
  { id: "treinar_triagem", fields: [] },
  { id: "acompanhar_email", fields: [] },
  { id: "otimizar_busca", fields: [] },
];

/** Monta o pedido para a IA que a pessoa ligou por MCP. Nada é processado nem cobrado pelo JobDragonQuest. */
export function buildRequest(assistant: string, language: string, values: Record<string, string>): string {
  const dados = Object.fromEntries(Object.entries(values).filter(([, value]) => value.trim() !== ""));
  return `Use a ferramenta jobdragonquest_assistente com ${JSON.stringify({ assistente: assistant, idioma: language || undefined, dados })} e siga o roteiro.`;
}

/** Link público de busca: a pessoa abre e consulta com a própria conta, sem robô e sem base paga. */
export function peopleSearchUrl(company: string, role: string): string {
  return `https://www.linkedin.com/search/results/people/?keywords=${encodeURIComponent(`${role} ${company}`.trim())}`;
}

export function AssistantsPage() {
  const { t } = useI18n();
  const [selected, setSelected] = useState("entrevista");
  const [language, setLanguage] = useState("");
  const [values, setValues] = useState<Record<string, string>>({});
  const [copied, setCopied] = useState(false);
  const [company, setCompany] = useState("");
  const [role, setRole] = useState("");
  const k = (key: string) => t(key as MessageKey);
  const fields = ASSISTANTS.find((a) => a.id === selected)?.fields ?? [];
  const request = buildRequest(selected, language, Object.fromEntries(fields.map((f) => [f, values[f] ?? ""])));
  const copy = () => void navigator.clipboard.writeText(request).then(() => setCopied(true));
  return (
    <>
      <div className="page-header"><h1>{t("assistants.title")}</h1></div>
      <Notice tone="info">{t("assistants.free")}</Notice>
      <Card title={t("assistants.choose")}>
        <div className="form-grid">
          <Field label={t("assistants.assistant")}>
            <select value={selected} onChange={(e) => { setSelected(e.target.value); setCopied(false); }}>
              {ASSISTANTS.map((a) => <option key={a.id} value={a.id}>{k(`assistants.item.${a.id}`)}</option>)}
            </select>
          </Field>
          <Field label={t("assistants.language")}>
            <input maxLength={40} value={language} onChange={(e) => setLanguage(e.target.value)} />
          </Field>
          {fields.map((field) => (
            <Field key={field} label={k(`assistants.field.${field}`)} wide={field === "jobDescription"}>
              {field === "jobDescription"
                ? <textarea rows={4} maxLength={1000} value={values[field] ?? ""} onChange={(e) => setValues({ ...values, [field]: e.target.value })} />
                : <input maxLength={200} value={values[field] ?? ""} onChange={(e) => setValues({ ...values, [field]: e.target.value })} />}
            </Field>
          ))}
        </div>
        <p className="prewrap small"><code>{request}</code></p>
        <Button variant="primary" onClick={copy}>{copied ? t("assistants.copied") : t("assistants.copy")}</Button>
      </Card>
      <Card title={t("assistants.people")}>
        <p className="muted small">{t("assistants.peopleHint")}</p>
        <div className="form-grid">
          <Field label={t("assistants.company")}><input maxLength={120} value={company} onChange={(e) => setCompany(e.target.value)} /></Field>
          <Field label={t("assistants.role")}><input maxLength={120} value={role} onChange={(e) => setRole(e.target.value)} /></Field>
        </div>
        {company.trim() !== "" && <a href={peopleSearchUrl(company, role)} target="_blank" rel="noopener noreferrer">{t("assistants.openSearch")}</a>}
      </Card>
    </>
  );
}
