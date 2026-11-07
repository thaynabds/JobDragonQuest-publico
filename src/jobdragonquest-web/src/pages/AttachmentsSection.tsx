import { useState } from "react";

import { api } from "../api/endpoints";
import { Card, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";
import { DocumentList, saveDownload } from "./DocumentsSection";

/** Anexos da candidatura: currículo enviado, carta, portfólio (issue #124). */
export function AttachmentsSection({ applicationId }: { applicationId: string }) {
  const { t, formatDate, errorMessage } = useI18n();
  const list = useAsync(() => api.applicationDocuments(applicationId).then((r) => r.data), [applicationId]);
  const [notice, setNotice] = useState<{ tone: "error" | "success"; text: string } | null>(null);

  const act = async (action: () => Promise<void>) => {
    setNotice(null);
    try {
      await action();
    } catch (err) {
      setNotice({ tone: "error", text: errorMessage(err) });
    }
  };

  return (
    <Card title={t("documents.attachments")}>
      {notice && (
        <Notice tone={notice.tone} onClose={() => setNotice(null)}>
          {notice.text}
        </Notice>
      )}
      <label className="btn btn-secondary">
        {t("documents.attach")}
        <input
          type="file"
          accept=".pdf,.docx,.txt,.png,.jpg,.jpeg"
          className="visually-hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            e.target.value = "";
            if (!file) return;
            if (file.size > 5 * 1024 * 1024) {
              setNotice({ tone: "error", text: t("documents.tooBig") });
              return;
            }
            void act(async () => {
              await api.saveApplicationDocument(applicationId, file);
              list.reload();
              setNotice({ tone: "success", text: t("common.saved") });
            });
          }}
        />
      </label>
      {list.error ? <Notice tone="error">{errorMessage(list.error)}</Notice> : null}
      {list.data && list.data.length === 0 && <p className="muted small">{t("documents.noAttachments")}</p>}
      <DocumentList
        items={list.data ?? []}
        onDownload={(doc) => void act(() => saveDownload(doc))}
        onDelete={(doc) =>
          void act(async () => {
            if (!window.confirm(t("common.confirmDelete"))) return;
            await api.deleteDocument(doc.id);
            list.reload();
          })
        }
        describe={(doc) => `${formatDate(doc.createdAt)} · ${Math.ceil(doc.sizeBytes / 1024)} KB`}
      />
    </Card>
  );
}
