import { useState, type FormEvent } from "react";

import { api } from "../api/endpoints";
import { CONTACT_ROLES, type ContactInput, type ContactRole } from "../api/types";
import { Badge, Button, Card, ExternalLink, Field, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";

const blank = (value: string) => (value.trim() === "" ? null : value.trim());
const empty: ContactInput = {
  role: "RECRUITER",
  name: "",
  position: "",
  email: "",
  linkedinUrl: "",
  notes: "",
  contacted: false,
};

/** Contatos de recrutadores, gestores e indicações de uma candidatura (issue #108). */
export function ContactsSection({ applicationId, onChanged }: { applicationId: string; onChanged: () => void }) {
  const { t, label, formatDate, errorMessage } = useI18n();
  const contacts = useAsync(() => api.contacts(applicationId).then((r) => r.data), [applicationId]);
  const [form, setForm] = useState<ContactInput>(empty);
  const [notice, setNotice] = useState<{
    tone: "error" | "success";
    text: string;
  } | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setNotice(null);
    try {
      await api.addContact(applicationId, {
        role: form.role,
        name: form.name.trim(),
        position: blank(form.position ?? ""),
        email: blank(form.email ?? ""),
        linkedinUrl: blank(form.linkedinUrl ?? ""),
        notes: blank(form.notes ?? ""),
        contacted: form.contacted,
      });
      setForm(empty);
      setNotice({ tone: "success", text: t("common.saved") });
      contacts.reload();
      onChanged();
    } catch (err) {
      setNotice({ tone: "error", text: errorMessage(err) });
    }
  };

  const remove = async (contactId: string) => {
    if (!window.confirm(t("common.confirmDelete"))) return;
    try {
      await api.deleteContact(applicationId, contactId);
      contacts.reload();
    } catch (err) {
      setNotice({ tone: "error", text: errorMessage(err) });
    }
  };

  return (
    <Card title={t("contacts.title")}>
      {notice && (
        <Notice tone={notice.tone} onClose={() => setNotice(null)}>
          {notice.text}
        </Notice>
      )}
      {contacts.error ? <Notice tone="error">{errorMessage(contacts.error)}</Notice> : null}
      {(contacts.data ?? []).length === 0 && !contacts.loading && <p className="muted small">{t("contacts.empty")}</p>}
      <ul className="contact-list">
        {(contacts.data ?? []).map((contact) => (
          <li key={contact.id}>
            <div>
              <strong>{contact.name}</strong> <Badge>{label("contactRole", contact.role)}</Badge>
              {contact.contactedAt && (
                <Badge tone="info">
                  {t("contacts.contactedAt", {
                    date: formatDate(contact.contactedAt),
                  })}
                </Badge>
              )}
            </div>
            {contact.position && <span className="small">{contact.position}</span>}
            {contact.email && <span className="small">{contact.email}</span>}
            {contact.linkedinUrl && <ExternalLink href={contact.linkedinUrl}>LinkedIn</ExternalLink>}
            {contact.notes && <p className="prewrap small">{contact.notes}</p>}
            <Button variant="danger" onClick={() => void remove(contact.id)}>
              {t("contacts.remove", { name: contact.name })}
            </Button>
          </li>
        ))}
      </ul>
      <form className="form-grid" onSubmit={(event) => void submit(event)} aria-label={t("contacts.add")}>
        <Field label={t("contacts.name")}>
          <input required maxLength={120} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </Field>
        <Field label={t("contacts.role")}>
          <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as ContactRole })}>
            {CONTACT_ROLES.map((role) => (
              <option key={role} value={role}>
                {label("contactRole", role)}
              </option>
            ))}
          </select>
        </Field>
        <Field label={t("contacts.position")}>
          <input maxLength={120} value={form.position ?? ""} onChange={(e) => setForm({ ...form, position: e.target.value })} />
        </Field>
        <Field label={t("contacts.email")}>
          <input type="email" maxLength={254} value={form.email ?? ""} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </Field>
        <Field label={t("contacts.linkedin")} wide>
          <input type="url" maxLength={500} value={form.linkedinUrl ?? ""} onChange={(e) => setForm({ ...form, linkedinUrl: e.target.value })} />
        </Field>
        <Field label={t("contacts.notes")} wide>
          <textarea rows={2} maxLength={2000} value={form.notes ?? ""} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
        </Field>
        <label className="check">
          <input type="checkbox" checked={form.contacted} onChange={(e) => setForm({ ...form, contacted: e.target.checked })} />
          {t("contacts.contacted")}
        </label>
        <div className="field">
          <Button type="submit" variant="primary" disabled={!form.name.trim()}>
            {t("contacts.add")}
          </Button>
        </div>
      </form>
    </Card>
  );
}
