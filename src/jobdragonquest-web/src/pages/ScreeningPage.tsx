import { useEffect, useMemo, useState } from "react";

import { api } from "../api/endpoints";
import type { Answer, ProfileInput } from "../api/types";
import { Button, Card, Field, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import type { MessageKey } from "../i18n/messages";
import { useAsync } from "../utils/useAsync";
import { toInput } from "./ProfileEditorPage";

export const SCREENING_CATEGORIES = ["ADDRESS", "EMPLOYMENT", "EDUCATION", "LANGUAGE", "NATIONALITY", "NOTICE", "PORTFOLIO", "RIGHTS", "WORK_MODE", "EXPERIENCE", "DISABILITY", "SUMMARY", "OTHER"] as const;
const PAGE = 10;

/** Filtra sem acento e sem diferença de maiúsculas, em pergunta e resposta. */
export function filterAnswers(answers: Answer[], query: string, category: string): Answer[] {
  const plain = (text: string) => text.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
  const needle = plain(query.trim());
  return answers.filter((a) => (!category || (a.category ?? "OTHER") === category) && (!needle || plain(`${a.question} ${a.answer}`).includes(needle)));
}

export function ScreeningPage() {
  const { t, locale, errorMessage } = useI18n();
  const profiles = useAsync(() => api.profiles().then((r) => r.data), []);
  const [profileId, setProfileId] = useState("");
  const [form, setForm] = useState<ProfileInput | null>(null);
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<number | null>(null);
  const [draft, setDraft] = useState<Answer>({ question: "", answer: "" });
  const [error, setError] = useState<string | null>(null);
  const active = profileId || profiles.data?.[0]?.id || "";

  useEffect(() => {
    if (!active) return;
    api.profile(active).then(({ data }) => setForm(toInput(data, locale))).catch((err: unknown) => setError(errorMessage(err)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active]);

  const answers = useMemo(() => form?.answers ?? [], [form]);
  const visible = filterAnswers(answers, query, category);
  const pages = Math.max(1, Math.ceil(visible.length / PAGE));
  const shown = visible.slice(page * PAGE, (page + 1) * PAGE);

  const save = async (next: Answer[]) => {
    if (!form) return;
    try {
      const { data } = await api.updateProfile(active, { ...form, answers: next });
      setForm(toInput(data, locale));
      setEditing(null);
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    }
  };
  const today = new Date().toISOString().slice(0, 10);
  const commit = () => {
    if (!draft.question.trim() || !draft.answer.trim() || editing === null) return;
    const item = { ...draft, updatedOn: today };
    void save(editing < 0 ? [item, ...answers] : answers.map((a, i) => (i === editing ? item : a)));
  };

  if (profiles.loading) return <Loading />;
  return (
    <>
      <div className="page-header">
        <h1>{t("screening.title")}</h1>
        <Button variant="primary" onClick={() => { setDraft({ question: "", answer: "", category: "OTHER" }); setEditing(-1); }}>{t("common.add")}</Button>
      </div>
      {error && <Notice tone="error">{error}</Notice>}
      <Card>
        <p className="muted small">{t("screening.count", { count: answers.length })}</p>
        <div className="filters">
          <select aria-label={t("screening.profile")} value={active} onChange={(e) => { setProfileId(e.target.value); setPage(0); }}>
            {(profiles.data ?? []).map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
          <input aria-label={t("screening.search")} placeholder={t("screening.search")} value={query} onChange={(e) => { setQuery(e.target.value); setPage(0); }} />
          <select aria-label={t("screening.category")} value={category} onChange={(e) => { setCategory(e.target.value); setPage(0); }}>
            <option value="">{t("common.all")}</option>
            {SCREENING_CATEGORIES.map((c) => <option key={c} value={c}>{t(`screening.cat.${c}` as MessageKey)}</option>)}
          </select>
        </div>
      </Card>
      {editing !== null && (
        <Card>
          <div className="form-grid">
            <Field label={t("screening.category")}>
              <select value={draft.category ?? "OTHER"} onChange={(e) => setDraft({ ...draft, category: e.target.value })}>
                {SCREENING_CATEGORIES.map((c) => <option key={c} value={c}>{t(`screening.cat.${c}` as MessageKey)}</option>)}
              </select>
            </Field>
            <Field label={t("screening.question")} wide><input maxLength={500} value={draft.question} onChange={(e) => setDraft({ ...draft, question: e.target.value })} /></Field>
            <Field label={t("screening.answer")} wide><textarea rows={3} maxLength={4000} value={draft.answer} onChange={(e) => setDraft({ ...draft, answer: e.target.value })} /></Field>
          </div>
          <div className="row-actions">
            <Button variant="primary" onClick={commit}>{t("common.save")}</Button>
            <Button variant="ghost" onClick={() => setEditing(null)}>{t("common.cancel")}</Button>
          </div>
        </Card>
      )}
      {shown.map((answer) => {
        const index = answers.indexOf(answer);
        return (
          <Card key={index}>
            <p className="small muted">{t(`screening.cat.${answer.category ?? "OTHER"}` as MessageKey)}{answer.updatedOn ? ` · ${answer.updatedOn}` : ""}</p>
            <p><strong>{answer.question}</strong></p>
            <p className="prewrap">{answer.answer}</p>
            <div className="row-actions">
              <Button variant="ghost" onClick={() => { setDraft(answer); setEditing(index); }}>{t("common.edit")}</Button>
              <Button variant="ghost" onClick={() => { if (window.confirm(t("common.confirmDelete"))) void save(answers.filter((_, i) => i !== index)); }}>{t("common.delete")}</Button>
            </div>
          </Card>
        );
      })}
      {visible.length === 0 && <p className="muted">{t("common.empty")}</p>}
      {pages > 1 && (
        <div className="row-actions">
          <Button disabled={page === 0} onClick={() => setPage(page - 1)}>‹</Button>
          <span>{page + 1} / {pages}</span>
          <Button disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>›</Button>
        </div>
      )}
    </>
  );
}
