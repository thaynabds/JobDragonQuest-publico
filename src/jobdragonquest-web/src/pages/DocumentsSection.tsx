import { useState } from "react";

import { api } from "../api/endpoints";
import { DOCUMENT_KINDS, type DocumentKind, type DocumentReading, type ResumeItem, type Profile, type StoredDocument } from "../api/types";
import { Button, Card, Field, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";

const MAX_BYTES = 5 * 1024 * 1024;
const ACCEPT =
  ".pdf,.docx,.odt,.xlsx,.ods,.csv,.tsv,.txt,.md,.markdown,.png,.jpg,.jpeg";

interface Draft {
  kind: "COURSE" | "CERTIFICATION";
  title: string;
  organization: string;
  endDate: string;
  hours: string;
  credentialId: string;
  url: string;
  skills: string;
}

/** Baixa um documento com o token (o navegador não manda autenticação num link direto). */
export async function saveDownload(document_: { id: string; name: string }) {
  const blob = await api.downloadDocument(document_.id);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = document_.name;
  link.rel = "noopener";
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/**
 * Documentos do perfil e atualização do currículo (issue #124). Guardar currículos com versão, cartas e certificados;
 * ler um certificado, currículo antigo, foto ou link e conferir a proposta antes de acrescentar ao perfil.
 */
export function DocumentsSection({ profileId, onAdded }: { profileId: string; onAdded: (profile: Profile) => void }) {
  const { t, label, formatDate, errorMessage } = useI18n();
  const list = useAsync(() => api.profileDocuments(profileId).then((r) => r.data), [profileId]);
  const [kind, setKind] = useState<DocumentKind>("RESUME");
  const [link, setLink] = useState("");
  const [reading, setReading] = useState<DocumentReading | null>(null);
  const [resumeItems, setResumeItems] = useState<(ResumeItem & { selected: boolean })[]>([]);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [notice, setNotice] = useState<{ tone: "error" | "success" | "info"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const act = async (action: () => Promise<void>) => {
    setNotice(null);
    setBusy(true);
    try {
      await action();
    } catch (err) {
      setNotice({ tone: "error", text: errorMessage(err) });
    } finally {
      setBusy(false);
    }
  };

  const tooBig = (file: File) => {
    if (file.size > MAX_BYTES) {
      setNotice({ tone: "error", text: t("documents.tooBig") });
      return true;
    }
    return false;
  };

  const show = (result: DocumentReading) => {
    setReading(result);
    setResumeItems(result.resume?.items.map((item) => ({ ...item, selected: true })) ?? []);
    if (result.resume) {
      setDraft(null);
      return;
    }
    const p = result.proposal;
    setDraft({
      kind: p.kind,
      title: p.title ?? "",
      organization: p.organization ?? "",
      endDate: p.date ?? "",
      hours: p.hours === null ? "" : String(p.hours),
      credentialId: p.credentialId ?? "",
      url: p.url ?? "",
      skills: p.skills.join(", "),
    });
  };

  const addResume = () =>
    act(async () => {
      const chosen = resumeItems.filter((item) => item.selected && item.title.trim());
      let last: Profile | null = null;
      for (const { selected: _selected, ...item } of chosen) {
        last = (await api.appendEntry(profileId, { ...item, title: item.title.trim(), status: item.current ? "IN_PROGRESS" : "COMPLETED" })).data;
      }
      if (last) onAdded(last);
      setReading(null);
      setResumeItems([]);
      setNotice({ tone: "success", text: t("documents.resumeAdded", { count: chosen.length }) });
    });

  const add = () =>
    act(async () => {
      if (!draft || !draft.title.trim()) return;
      const { data } = await api.appendEntry(profileId, {
        kind: draft.kind,
        title: draft.title.trim(),
        organization: draft.organization.trim() || null,
        endDate: draft.endDate || null,
        hours: draft.hours ? Number(draft.hours) : null,
        credentialId: draft.credentialId.trim() || null,
        url: draft.url.trim() || null,
        skills: draft.skills.trim() || null,
        status: "COMPLETED",
      });
      onAdded(data);
      setDraft(null);
      setReading(null);
      setNotice({ tone: "success", text: t("documents.added") });
    });

  return (
    <Card title={t("documents.title")}>
      <p className="muted small">{t("documents.intro")}</p>
      <p className="small"><strong>{t("documents.formats")}</strong></p>
      {notice && (
        <Notice tone={notice.tone} onClose={() => setNotice(null)}>
          {notice.text}
        </Notice>
      )}

      <h3>{t("documents.readTitle")}</h3>
      <div className="filters">
        <label className="btn btn-secondary">
          {t("documents.readFile")}
          <input
            type="file"
            accept={ACCEPT}
            className="visually-hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              e.target.value = "";
              if (file && !tooBig(file)) void act(async () => show((await api.readDocument(file)).data));
            }}
          />
        </label>
        <Field label={t("documents.readLink")}>
          <input type="url" maxLength={2048} value={link} onChange={(e) => setLink(e.target.value)} />
        </Field>
        <Button disabled={!link.trim() || busy} onClick={() => void act(async () => show((await api.readLinkDocument(link.trim())).data))}>
          {t("documents.readLinkButton")}
        </Button>
      </div>

      {reading?.ocrUnavailable && <Notice tone="info">{t("documents.ocrUnavailable")}</Notice>}
      {draft && (
        <form
          className="form-grid"
          aria-label={t("documents.proposal")}
          onSubmit={(e) => {
            e.preventDefault();
            void add();
          }}
        >
          <p className="field-wide muted small">{t("documents.confirm")}</p>
          <Field label={t("documents.kind")}>
            <select value={draft.kind} onChange={(e) => setDraft({ ...draft, kind: e.target.value as Draft["kind"] })}>
              <option value="COURSE">{label("entryKind", "COURSE")}</option>
              <option value="CERTIFICATION">{label("entryKind", "CERTIFICATION")}</option>
            </select>
          </Field>
          <Field label={t("documents.entryTitle")}>
            <input required maxLength={200} value={draft.title} onChange={(e) => setDraft({ ...draft, title: e.target.value })} />
          </Field>
          <Field label={t("documents.issuer")}>
            <input maxLength={200} value={draft.organization} onChange={(e) => setDraft({ ...draft, organization: e.target.value })} />
          </Field>
          <Field label={t("documents.date")}>
            <input type="date" value={draft.endDate} onChange={(e) => setDraft({ ...draft, endDate: e.target.value })} />
          </Field>
          <Field label={t("documents.hours")}>
            <input type="number" min={0} max={5000} value={draft.hours} onChange={(e) => setDraft({ ...draft, hours: e.target.value })} />
          </Field>
          <Field label={t("documents.credential")}>
            <input maxLength={120} value={draft.credentialId} onChange={(e) => setDraft({ ...draft, credentialId: e.target.value })} />
          </Field>
          <Field label={t("documents.url")} wide>
            <input type="url" maxLength={2048} value={draft.url} onChange={(e) => setDraft({ ...draft, url: e.target.value })} />
          </Field>
          <Field label={t("documents.skills")} wide>
            <input maxLength={500} value={draft.skills} onChange={(e) => setDraft({ ...draft, skills: e.target.value })} />
          </Field>
          <div className="field">
            <Button type="submit" variant="primary" disabled={busy || !draft.title.trim()}>
              {t("documents.add")}
            </Button>{" "}
            <Button onClick={() => setDraft(null)}>{t("common.cancel")}</Button>
          </div>
          {reading && reading.text && (
            <details className="field-wide">
              <summary>{t("documents.text")}</summary>
              <p className="prewrap small">{reading.text.slice(0, 5000)}</p>
            </details>
          )}
        </form>
      )}

      {reading?.resume && (
        <form
          aria-label={t("documents.resumeTitle")}
          onSubmit={(e) => {
            e.preventDefault();
            void addResume();
          }}
        >
          <h3>{t("documents.resumeTitle")}</h3>
          <p className="muted small">{t("documents.resumeIntro", { count: resumeItems.length })}</p>
          {reading.resume.summary && <p className="small prewrap">{reading.resume.summary}</p>}
          <ul className="resume-reading">
            {resumeItems.map((item, index) => (
              <li key={index}>
                <input
                  type="checkbox"
                  aria-label={item.title}
                  checked={item.selected}
                  onChange={(e) => setResumeItems(resumeItems.map((x, i) => (i === index ? { ...x, selected: e.target.checked } : x)))}
                />
                <span className="small muted">{label("entryKind", item.kind)}</span>
                <input
                  aria-label={t("documents.entryTitle")}
                  maxLength={200}
                  value={item.title}
                  onChange={(e) => setResumeItems(resumeItems.map((x, i) => (i === index ? { ...x, title: e.target.value } : x)))}
                />
                <span className="small muted">{[item.organization, item.startDate, item.current ? t("resume.current") : item.endDate].filter(Boolean).join(" · ")}</span>
              </li>
            ))}
          </ul>
          <Button type="submit" variant="primary" disabled={busy || !resumeItems.some((x) => x.selected)}>
            {t("documents.resumeAdd")}
          </Button>
          <details>
            <summary>{t("documents.text")}</summary>
            <p className="prewrap small">{reading.text.slice(0, 5000)}</p>
          </details>
        </form>
      )}

      <h3>{t("documents.storedTitle")}</h3>
      <div className="filters">
        <Field label={t("documents.kind")}>
          <select value={kind} onChange={(e) => setKind(e.target.value as DocumentKind)}>
            {DOCUMENT_KINDS.map((k) => (
              <option key={k} value={k}>
                {label("documentKind", k)}
              </option>
            ))}
          </select>
        </Field>
        <label className="btn btn-secondary">
          {t("documents.save")}
          <input
            type="file"
            accept={ACCEPT}
            className="visually-hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              e.target.value = "";
              if (file && !tooBig(file))
                void act(async () => {
                  await api.saveProfileDocument(profileId, kind, file);
                  list.reload();
                  setNotice({ tone: "success", text: t("common.saved") });
                });
            }}
          />
        </label>
      </div>
      {list.error ? <Notice tone="error">{errorMessage(list.error)}</Notice> : null}
      {list.data && list.data.length === 0 && <p className="muted small">{t("documents.empty")}</p>}
      <DocumentList
        items={list.data ?? []}
        onRead={(doc) => void act(async () => show((await api.readStoredDocument(doc.id)).data))}
        onDelete={(doc) =>
          void act(async () => {
            if (!window.confirm(t("common.confirmDelete"))) return;
            await api.deleteDocument(doc.id);
            list.reload();
          })
        }
        onDownload={(doc) => void act(() => saveDownload(doc))}
        describe={(doc) => `${label("documentKind", doc.kind)} · v${doc.version} · ${formatDate(doc.createdAt)} · ${Math.ceil(doc.sizeBytes / 1024)} KB`}
      />
    </Card>
  );
}

export function DocumentList({
  items,
  onRead,
  onDelete,
  onDownload,
  describe,
}: {
  items: StoredDocument[];
  onRead?: (doc: StoredDocument) => void;
  onDelete: (doc: StoredDocument) => void;
  onDownload: (doc: StoredDocument) => void;
  describe: (doc: StoredDocument) => string;
}) {
  const { t } = useI18n();
  return (
    <ul className="contact-list">
      {items.map((doc) => (
        <li key={doc.id}>
          <strong>{doc.name}</strong>
          <span className="small muted">{describe(doc)}</span>
          <div className="row-actions">
            <Button onClick={() => onDownload(doc)}>{t("documents.download", { name: doc.name })}</Button>
            {onRead && <Button onClick={() => onRead(doc)}>{t("documents.readStored", { name: doc.name })}</Button>}
            <Button variant="danger" onClick={() => onDelete(doc)}>
              {t("documents.delete", { name: doc.name })}
            </Button>
          </div>
        </li>
      ))}
    </ul>
  );
}
