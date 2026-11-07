import { useState } from "react";

import { api } from "../api/endpoints";
import { DOCUMENT_KINDS, type DocumentKind, type DocumentReading, type Profile, type StoredDocument } from "../api/types";
import { Button, Card, Field, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";

const MAX_BYTES = 5 * 1024 * 1024;
const ACCEPT =
  ".pdf,.docx,.txt,.png,.jpg,.jpeg,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,image/png,image/jpeg";

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
