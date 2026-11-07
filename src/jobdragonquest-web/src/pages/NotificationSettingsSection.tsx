import { useEffect, useState } from "react";

import { api } from "../api/endpoints";
import type { NotificationPreference, NotificationSettings, PreferenceView } from "../api/types";
import { Badge, Button, Card, Field, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import type { MessageKey } from "../i18n/messages";
import { useNotifications } from "../notifications/NotificationsContext";

const GROUPS = ["STAGE", "PENDING", "SOURCE"] as const;
const CHANNELS = ["enabled", "popup", "system", "sound"] as const;

/** "1440, 60" → [1440, 60]; vazio → null (a API usa o padrão). Valores inválidos ficam para a API recusar. */
export function parseLeads(text: string): number[] | null {
  const values = text
    .split(",")
    .map((part) => part.trim())
    .filter(Boolean)
    .map(Number);
  return values.length === 0 ? null : values;
}

function priorityTone(priority: string): "bad" | "warn" | "neutral" {
  return priority === "HIGH" ? "bad" : priority === "MEDIUM" ? "warn" : "neutral";
}

export function NotificationSettingsSection() {
  const { t, label, errorMessage } = useI18n();
  const { setHideDetails, permission, requestPermission, pollNow } = useNotifications();
  const [rows, setRows] = useState<PreferenceView[] | null>(null);
  const [settings, setSettings] = useState<NotificationSettings | null>(null);
  const [timeZone, setTimeZone] = useState("");
  const [leadText, setLeadText] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<{ tone: "error" | "success"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const apply = (view: { settings: NotificationSettings; timeZone: string; preferences: PreferenceView[] }) => {
    setRows(view.preferences);
    setSettings(view.settings);
    setTimeZone(view.timeZone);
    setLeadText(
      Object.fromEntries(view.preferences.filter((row) => row.supportsLead).map((row) => [row.topic, (row.preference.leadMinutes ?? []).join(", ")])),
    );
  };

  useEffect(() => {
    api
      .notificationPreferences()
      .then((response) => apply(response.data))
      .catch((error: unknown) => setMessage({ tone: "error", text: errorMessage(error) }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const change = (topic: string, patch: Partial<NotificationPreference>) =>
    setRows((current) => current?.map((row) => (row.topic === topic ? { ...row, preference: { ...row.preference, ...patch } } : row)) ?? null);

  const save = async () => {
    if (!rows || !settings) {
      return;
    }
    setBusy(true);
    setMessage(null);
    try {
      const preferences = rows.map((row) => ({
        ...row.preference,
        leadMinutes: row.supportsLead ? parseLeads(leadText[row.topic] ?? "") : null,
        minScore: row.supportsMinScore ? row.preference.minScore : null,
        staleDays: row.supportsStaleDays ? row.preference.staleDays : null,
      }));
      const response = await api.updateNotificationPreferences({ settings, preferences });
      apply(response.data);
      setHideDetails(response.data.settings.hideDetails);
      setMessage({ tone: "success", text: t("common.saved") });
    } catch (error) {
      setMessage({ tone: "error", text: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  const rowLabel = (row: PreferenceView) =>
    row.group === "STAGE" ? label("state", row.key) : row.group === "SOURCE" ? (row.label ?? row.key) : t(`notif.type.${row.key}` as MessageKey);

  if (!rows || !settings) {
    return (
      <Card title={t("notif.settingsTitle")}>
        {message ? <Notice tone={message.tone}>{message.text}</Notice> : <Loading />}
      </Card>
    );
  }

  return (
    <Card
      title={t("notif.settingsTitle")}
      actions={
        <Button variant="primary" onClick={() => void save()} disabled={busy}>
          {t("common.save")}
        </Button>
      }
    >
      <p className="muted">{t("notif.settingsIntro")}</p>
      {message && (
        <Notice tone={message.tone} onClose={() => setMessage(null)}>
          {message.text}
        </Notice>
      )}
      <div className="form-row">
        {permission === "default" && (
          <Button variant="primary" onClick={() => void requestPermission()}>
            {t("notif.enableSystem")}
          </Button>
        )}
        {permission === "granted" && <Badge tone="good">{t("notif.systemOn")}</Badge>}
        {permission === "denied" && <span className="muted">{t("notif.systemDenied")}</span>}
        {permission === "unsupported" && <span className="muted">{t("notif.systemUnsupported")}</span>}
        <Button
          onClick={() =>
            void api
              .testNotification()
              .then(pollNow)
              .catch((error: unknown) => setMessage({ tone: "error", text: errorMessage(error) }))
          }
        >
          {t("notif.test")}
        </Button>
      </div>

      <fieldset className="settings-group">
        <legend>{t("notif.quietTitle")}</legend>
        <label className="check">
          <input type="checkbox" checked={settings.quietEnabled} onChange={(event) => setSettings({ ...settings, quietEnabled: event.target.checked })} />
          <span>{t("notif.quietEnabled", { zone: timeZone })}</span>
        </label>
        <div className="form-row">
          <Field label={t("notif.quietStart")}>
            <input type="time" value={settings.quietStart} onChange={(event) => setSettings({ ...settings, quietStart: event.target.value })} />
          </Field>
          <Field label={t("notif.quietEnd")}>
            <input type="time" value={settings.quietEnd} onChange={(event) => setSettings({ ...settings, quietEnd: event.target.value })} />
          </Field>
        </div>
        <label className="check">
          <input type="checkbox" checked={settings.quietAllowHigh} onChange={(event) => setSettings({ ...settings, quietAllowHigh: event.target.checked })} />
          <span>{t("notif.quietAllowHigh")}</span>
        </label>
        <label className="check">
          <input type="checkbox" checked={settings.hideDetails} onChange={(event) => setSettings({ ...settings, hideDetails: event.target.checked })} />
          <span>{t("notif.hideDetails")}</span>
        </label>
      </fieldset>

      {GROUPS.map((group) => {
        const groupRows = rows.filter((row) => row.group === group);
        if (groupRows.length === 0) {
          return null;
        }
        return (
          <div key={group} className="table-scroll">
            <table className="table settings-table">
              <caption>{t(`notif.group.${group}` as MessageKey)}</caption>
              <thead>
                <tr>
                  <th scope="col">{t("notif.col.item")}</th>
                  <th scope="col">{t("notif.col.priority")}</th>
                  {CHANNELS.map((channel) => (
                    <th key={channel} scope="col">
                      {t(`notif.col.${channel}` as MessageKey)}
                    </th>
                  ))}
                  <th scope="col">{t("notif.col.extra")}</th>
                </tr>
              </thead>
              <tbody>
                {groupRows.map((row) => {
                  const name = rowLabel(row);
                  return (
                    <tr key={row.topic}>
                      <th scope="row">{name}</th>
                      <td>
                        <Badge tone={priorityTone(row.priority)}>{t(`notif.priority.${row.priority}` as MessageKey)}</Badge>
                      </td>
                      {CHANNELS.map((channel) => (
                        <td key={channel}>
                          <input
                            type="checkbox"
                            aria-label={`${name}: ${t(`notif.col.${channel}` as MessageKey)}`}
                            checked={row.preference[channel]}
                            disabled={channel !== "enabled" && !row.preference.enabled}
                            onChange={(event) => change(row.topic, { [channel]: event.target.checked })}
                          />
                        </td>
                      ))}
                      <td>
                        {row.supportsLead && (
                          <input
                            aria-label={`${name}: ${t("notif.leadMinutes")}`}
                            title={t("notif.leadHint")}
                            value={leadText[row.topic] ?? ""}
                            onChange={(event) => setLeadText({ ...leadText, [row.topic]: event.target.value })}
                            placeholder={t("notif.leadMinutes")}
                          />
                        )}
                        {row.supportsStaleDays && (
                          <input
                            type="number"
                            min={1}
                            max={90}
                            aria-label={`${name}: ${t("notif.staleDays")}`}
                            value={row.preference.staleDays ?? 14}
                            onChange={(event) => change(row.topic, { staleDays: Number(event.target.value) })}
                          />
                        )}
                        {row.supportsMinScore && (
                          <input
                            type="number"
                            min={0}
                            max={100}
                            aria-label={`${name}: ${t("notif.minScore")}`}
                            placeholder={t("notif.minScore")}
                            value={row.preference.minScore ?? ""}
                            onChange={(event) => change(row.topic, { minScore: event.target.value === "" ? null : Number(event.target.value) })}
                          />
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        );
      })}
      <p className="muted">{t("notif.leadHint")}</p>
    </Card>
  );
}
