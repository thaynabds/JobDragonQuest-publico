import { useState, type FormEvent } from "react";

import { api } from "../api/endpoints";
import type { Contest, ContestInput, ContestStage } from "../api/types";
import { Button, Card, Field, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import type { MessageKey } from "../i18n/messages";
import { useAsync } from "../utils/useAsync";

const STATUSES = ["PLANNED", "OPEN", "DONE", "MISSED", "NOT_APPLICABLE"] as const;
const EMPTY: ContestInput = { name: "", organ: null, board: null, notice: null, roleName: null, registration: null, searchName: null, validUntil: null, notes: null, stages: [] };
const blank = (value: string) => (value.trim() === "" ? null : value);

/** Concursos: etapas e prazos, páginas oficiais vigiadas e possíveis menções, sempre para conferir na fonte. */
export function ContestsPage() {
  const { t, errorMessage } = useI18n();
  const list = useAsync(() => api.contests().then((r) => r.data), []);
  const [form, setForm] = useState<ContestInput | null>(null);
  const [editId, setEditId] = useState<string | null>(null);
  const [message, setMessage] = useState<{ tone: "success" | "error"; text: string } | null>(null);
  const [urls, setUrls] = useState<Record<string, string>>({});
  const run = async (action: () => Promise<unknown>, done?: string) => {
    setMessage(null);
    try {
      await action();
      if (done) setMessage({ tone: "success", text: done });
      list.reload();
    } catch (err) {
      setMessage({ tone: "error", text: errorMessage(err) });
    }
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!form) return;
    void run(async () => {
      await (editId ? api.updateContest(editId, form) : api.createContest(form));
      setForm(null);
      setEditId(null);
    }, t("common.saved"));
  };
  const text = (field: keyof ContestInput, key: string, max: number, type = "text") => form && (
    <Field label={t(key as MessageKey)}>
      <input type={type} maxLength={max} required={field === "name"} value={(form[field] as string | null) ?? ""} onChange={(e) => setForm({ ...form, [field]: field === "name" ? e.target.value : blank(e.target.value) })} />
    </Field>
  );
  const setStage = (index: number, change: Partial<ContestStage>) => form && setForm({ ...form, stages: form.stages.map((s, i) => (i === index ? { ...s, ...change } : s)) });
  const edit = (c: Contest) => {
    const { id, watches: _w, documents: _d, matches: _m, ...rest } = c;
    setEditId(id);
    setForm(rest);
  };
  if (list.loading && !list.data) return <Loading />;
  return (
    <>
      <div className="page-header">
        <h1>{t("contests.title")}</h1>
        <Button variant="primary" onClick={() => { setEditId(null); setForm({ ...EMPTY }); }}>{t("common.add")}</Button>
      </div>
      <Notice tone="info">{t("contests.warning")}</Notice>
      {message && <Notice tone={message.tone}>{message.text}</Notice>}
      {form && (
        <Card>
          <form onSubmit={submit} className="form-grid">
            {text("name", "contests.name", 200)}
            {text("organ", "contests.organ", 200)}
            {text("board", "contests.board", 120)}
            {text("notice", "contests.notice", 120)}
            {text("roleName", "contests.role", 200)}
            {text("registration", "contests.registration", 40)}
            {text("searchName", "contests.searchName", 160)}
            {text("validUntil", "contests.validUntil", 10, "date")}
            <fieldset className="field-wide">
              <legend>{t("contests.stages")}</legend>
              {form.stages.map((stage, index) => (
                <div className="form-row" key={index}>
                  <input required aria-label={t("contests.stage")} placeholder={t("contests.stage")} maxLength={160} value={stage.title} onChange={(e) => setStage(index, { title: e.target.value })} />
                  <input type="date" aria-label={t("contests.date")} value={stage.date ?? ""} onChange={(e) => setStage(index, { date: e.target.value || null })} />
                  <input type="date" aria-label={t("contests.deadline")} value={stage.deadline ?? ""} onChange={(e) => setStage(index, { deadline: e.target.value || null })} />
                  <select aria-label={t("contests.status")} value={stage.status} onChange={(e) => setStage(index, { status: e.target.value as ContestStage["status"] })}>
                    {STATUSES.map((s) => <option key={s} value={s}>{t(`contests.status.${s}` as MessageKey)}</option>)}
                  </select>
                  <Button variant="ghost" onClick={() => setForm({ ...form, stages: form.stages.filter((_, i) => i !== index) })}>{t("common.remove")}</Button>
                </div>
              ))}
              <Button onClick={() => setForm({ ...form, stages: [...form.stages, { title: "", date: null, deadline: null, status: "PLANNED", url: null, note: null }] })}>{t("contests.addStage")}</Button>
            </fieldset>
            <div className="row-actions field-wide">
              <Button type="submit" variant="primary">{t("common.save")}</Button>
              <Button variant="ghost" onClick={() => setForm(null)}>{t("common.cancel")}</Button>
            </div>
          </form>
        </Card>
      )}
      {(list.data ?? []).map((c) => (
        <Card key={c.id} title={c.name} actions={(
          <>
            <Button onClick={() => edit(c)}>{t("common.edit")}</Button>
            <Button onClick={() => void run(() => api.scanContest(c.id), t("contests.scanned"))}>{t("contests.scan")}</Button>
            <Button variant="ghost" onClick={() => { if (window.confirm(t("common.confirmDelete"))) void run(() => api.deleteContest(c.id)); }}>{t("common.delete")}</Button>
          </>
        )}>
          <p className="small muted">{[c.organ, c.board, c.roleName].filter(Boolean).join(" · ")}</p>
          {c.stages.length > 0 && <ul>{c.stages.map((s, i) => <li key={i}>{s.title}{s.deadline ? ` · ${t("contests.deadline")}: ${s.deadline}` : ""} · {t(`contests.status.${s.status}` as MessageKey)}</li>)}</ul>}
          <h3>{t("contests.watches")}</h3>
          <ul>{c.watches.map((w) => (
            <li key={w.id}>
              <a href={w.url} target="_blank" rel="noopener noreferrer">{w.label ?? w.url}</a> · {w.status}{" "}
              <Button variant="ghost" onClick={() => void run(() => api.deleteContestWatch(c.id, w.id))}>{t("common.remove")}</Button>
            </li>
          ))}</ul>
          <div className="form-row">
            <input type="url" aria-label={t("contests.addWatch")} placeholder="https://" maxLength={2048} value={urls[c.id] ?? ""} onChange={(e) => setUrls({ ...urls, [c.id]: e.target.value })} />
            <Button disabled={!urls[c.id]} onClick={() => void run(async () => { await api.addContestWatch(c.id, urls[c.id] ?? ""); setUrls({ ...urls, [c.id]: "" }); })}>{t("contests.addWatch")}</Button>
          </div>
          {c.matches.length > 0 && (
            <>
              <h3>{t("contests.matches")}</h3>
              <ul>{c.matches.map((m, i) => (
                <li key={i}>
                  <strong>{t(`contests.confidence.${m.confidence}` as MessageKey)}</strong>: {m.line}{" "}
                  <a href={m.url} target="_blank" rel="noopener noreferrer">{t("contests.source")}</a>
                </li>
              ))}</ul>
            </>
          )}
          {c.documents.some((d) => d.status !== "READ") && <Notice tone="error">{t("contests.unread")}</Notice>}
        </Card>
      ))}
      {(list.data ?? []).length === 0 && !form && <p className="muted">{t("common.empty")}</p>}
    </>
  );
}
