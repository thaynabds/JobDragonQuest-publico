import { useMemo, useState, type FormEvent } from "react";

import { api } from "../api/endpoints";
import type { CatalogEntry, ProfileSummary, Source, SourceInput, SourceKind, SourceRun } from "../api/types";
import { Badge, Button, Card, Empty, ExternalLink, Field, Loading, Notice } from "../components/ui";
import { FrequencyInput, frequencyText } from "../components/FrequencyInput";
import { useI18n } from "../i18n/I18nContext";
import type { MessageKey } from "../i18n/messages";
import { splitList } from "../utils/safe";
import { useAsync } from "../utils/useAsync";


export interface DraftRow {
  name: string;
  kind: SourceKind;
  target: string;
}

const newRow = (): DraftRow => ({ name: "", kind: "GREENHOUSE", target: "" });

/** Converte as linhas do formulário no lote da API: ATS levam identificador, as demais levam URL. */
export function toSourceInputs(rows: DraftRow[], catalog: CatalogEntry[]): SourceInput[] {
  return rows
    .filter((row) => row.name.trim() !== "" || row.target.trim() !== "")
    .map((row) => {
      const entry = catalog.find((item) => item.kind === row.kind);
      const target = row.target.trim();
      const name = row.name.trim() || target;
      return entry?.needsIdentifier ? { name, kind: row.kind, identifier: target } : { name, kind: row.kind, url: target };
    });
}

export function SourcesPage() {
  const { t, label, formatDate, errorMessage } = useI18n();
  const catalog = useAsync(() => api.sourceCatalog().then((r) => r.data), []);
  const profiles = useAsync(() => api.profiles().then((r) => r.data), []);
  const sources = useAsync(() => api.sources().then((r) => r.data), []);

  const [rows, setRows] = useState<DraftRow[]>([newRow(), { ...newRow(), kind: "LINKEDIN" }]);
  const [profileIds, setProfileIds] = useState<string[]>([]);
  const [frequency, setFrequency] = useState<number>(360);
  const [keywords, setKeywords] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [bulkFrequency, setBulkFrequency] = useState<number>(1440);
  const [bulkProfile, setBulkProfile] = useState("");
  const [history, setHistory] = useState<{ sourceId: string; runs: SourceRun[] } | null>(null);
  const [notice, setNotice] = useState<{ tone: "error" | "success"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const profileNames = useMemo(() => new Map((profiles.data ?? []).map((p: ProfileSummary) => [p.id, p.name])), [profiles.data]);

  const run = async (action: () => Promise<string>) => {
    setBusy(true);
    setNotice(null);
    try {
      const text = await action();
      setNotice({ tone: "success", text });
      sources.reload();
    } catch (err) {
      setNotice({ tone: "error", text: errorMessage(err) });
    } finally {
      setBusy(false);
    }
  };

  const create = (event: FormEvent) => {
    event.preventDefault();
    const inputs = toSourceInputs(rows, catalog.data ?? []);
    if (inputs.length === 0) {
      return;
    }
    void run(async () => {
      const { data } = await api.createSources({ sources: inputs, profileIds, frequencyMinutes: frequency, keywords: splitList(keywords) });
      setRows([newRow()]);
      return t("sources.created", { created: data.created.length, duplicates: data.duplicates.length });
    });
  };

  const bulk = (change: Parameters<typeof api.updateSources>[0], success: string) =>
    run(async () => {
      await api.updateSources(change);
      return success;
    });

  const showHistory = async (source: Source) => {
    try {
      const { data } = await api.sourceRuns(source.id);
      setHistory({ sourceId: source.id, runs: data });
    } catch (err) {
      setNotice({ tone: "error", text: errorMessage(err) });
    }
  };

  const frequencyLabel = (minutes: number) => frequencyText(minutes, t);
  const toggle = (id: string) => setSelected((current) => (current.includes(id) ? current.filter((x) => x !== id) : [...current, id]));
  const all = sources.data ?? [];

  return (
    <div className="page">
      <h1>{t("sources.title")}</h1>
      <p className="muted">{t("sources.intro")}</p>
      {notice && (
        <Notice tone={notice.tone} onClose={() => setNotice(null)}>
          {notice.text}
        </Notice>
      )}

      <Card title={t("sources.addTitle")}>
        <form onSubmit={create}>
          <div className="table-scroll"><table className="table">
            <thead>
              <tr>
                <th>{t("sources.kind")}</th>
                <th>{t("sources.name")}</th>
                <th>{t("sources.target")} <span className="info-tip inline"><button type="button" aria-label={t("help.sources.target")}>i</button><span role="tooltip">{t("help.sources.target")}</span></span></th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((row, index) => {
                const entry = catalog.data?.find((item) => item.kind === row.kind);
                const update = (change: Partial<DraftRow>) => setRows(rows.map((r, i) => (i === index ? { ...r, ...change } : r)));
                return (
                  <tr key={index}>
                    <td>
                      <select aria-label={t("sources.kind")} value={row.kind} onChange={(e) => update({ kind: e.target.value as SourceKind })}>
                        {catalog.data?.map((item) => (
                          <option key={item.kind} value={item.kind}>
                            {item.label} · {t(`sources.mode.${item.mode}` as MessageKey)}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <input aria-label={t("sources.name")} value={row.name} maxLength={120} onChange={(e) => update({ name: e.target.value })} />
                    </td>
                    <td>
                      <input
                        aria-label={t("sources.target")}
                        value={row.target}
                        maxLength={entry?.needsIdentifier ? 100 : 2048}
                        placeholder={entry?.needsIdentifier ? t("sources.identifierPlaceholder") : t("sources.urlPlaceholder")}
                        onChange={(e) => update({ target: e.target.value })}
                      />
                    </td>
                    <td>
                      <Button variant="ghost" disabled={rows.length === 1} onClick={() => setRows(rows.filter((_, i) => i !== index))}>
                        {t("common.remove")}
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table></div>
          <Button onClick={() => setRows([...rows, newRow()])} disabled={rows.length >= 50}>
            {t("sources.addRow")}
          </Button>
          <div className="form-grid">
            <fieldset className="field field-wide">
              <legend className="field-label">{t("sources.profiles")}</legend>
              <div className="checks">
                {(profiles.data ?? []).map((profile) => (
                  <label key={profile.id} className="check">
                    <input
                      type="checkbox"
                      checked={profileIds.includes(profile.id)}
                      onChange={() =>
                        setProfileIds(profileIds.includes(profile.id) ? profileIds.filter((id) => id !== profile.id) : [...profileIds, profile.id])
                      }
                    />
                    {profile.name}
                  </label>
                ))}
                {profiles.data?.length === 0 && <span className="muted">{t("jobs.noProfiles")}</span>}
              </div>
            </fieldset>
            <Field label={t("sources.frequency")} info={t("help.sources.frequency")}>
              <FrequencyInput value={frequency} onChange={setFrequency} label={t("sources.frequency")} />
            </Field>
            <Field label={t("sources.keywords")} info={t("help.sources.keywords")} hint={t("sources.keywordsHint")} wide>
              <input value={keywords} onChange={(e) => setKeywords(e.target.value)} />
            </Field>
          </div>
          <Button type="submit" variant="primary" disabled={busy}>
            {t("sources.create")}
          </Button>
        </form>
      </Card>

      <Card title={t("sources.title")}>
        {selected.length > 0 && (
          <div className="bulk-bar" role="region" aria-label={t("sources.selected", { count: selected.length })}>
            <strong>{t("sources.selected", { count: selected.length })}</strong>
            <Button onClick={() => void bulk({ sourceIds: selected, enabled: false }, t("common.saved"))}>{t("sources.pause")}</Button>
            <Button onClick={() => void bulk({ sourceIds: selected, enabled: true }, t("common.saved"))}>{t("sources.resume")}</Button>
            <FrequencyInput value={bulkFrequency} onChange={setBulkFrequency} label={t("sources.frequency")} />
            <Button onClick={() => void bulk({ sourceIds: selected, frequencyMinutes: bulkFrequency }, t("common.saved"))}>
              {t("sources.setFrequency")}
            </Button>
            <select aria-label={t("sources.chooseProfile")} value={bulkProfile} onChange={(e) => setBulkProfile(e.target.value)}>
              <option value="">{t("sources.chooseProfile")}</option>
              {(profiles.data ?? []).map((profile) => (
                <option key={profile.id} value={profile.id}>
                  {profile.name}
                </option>
              ))}
            </select>
            <Button disabled={!bulkProfile} onClick={() => void bulk({ sourceIds: selected, addProfileIds: [bulkProfile] }, t("common.saved"))}>
              {t("sources.addProfile")}
            </Button>
            <Button disabled={!bulkProfile} onClick={() => void bulk({ sourceIds: selected, removeProfileIds: [bulkProfile] }, t("common.saved"))}>
              {t("sources.removeProfile")}
            </Button>
          </div>
        )}
        {sources.error ? <Notice tone="error">{errorMessage(sources.error)}</Notice> : null}
        {sources.loading && !sources.data ? <Loading /> : null}
        {sources.data && all.length === 0 && <Empty />}
        {all.length > 0 && (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>
                    <input
                      type="checkbox"
                      aria-label={t("common.all")}
                      checked={selected.length === all.length}
                      onChange={() => setSelected(selected.length === all.length ? [] : all.map((s) => s.id))}
                    />
                  </th>
                  <th>{t("sources.name")}</th>
                  <th>{t("sources.kind")}</th>
                  <th>{t("sources.profiles")}</th>
                  <th>{t("sources.frequency")}</th>
                  <th>{t("sources.status")}</th>
                  <th>{t("common.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {all.map((source) => (
                  <tr key={source.id}>
                    <td>
                      <input type="checkbox" aria-label={source.name} checked={selected.includes(source.id)} onChange={() => toggle(source.id)} />
                    </td>
                    <td>
                      <strong>{source.name}</strong>
                      <div className="small">
                        <ExternalLink href={source.openUrl}>{source.identifier ?? source.openUrl}</ExternalLink>
                      </div>
                      {source.keywords.length > 0 && <div className="small muted">{source.keywords.join(", ")}</div>}
                    </td>
                    <td>
                      {source.kindLabel}{" "}
                      <Badge tone={source.collectionMode === "AUTOMATIC" ? "good" : "info"}>
                        {t(`sources.mode.${source.collectionMode}` as MessageKey)}
                      </Badge>
                    </td>
                    <td className="small">
                      {source.profileIds.length === 0
                        ? t("sources.noProfiles")
                        : source.profileIds.map((id) => profileNames.get(id) ?? id).join(", ")}
                    </td>
                    <td className="small">{frequencyLabel(source.frequencyMinutes)}</td>
                    <td className="small">
                      {source.running ? (
                        <Badge tone="info">{t("sources.running")}</Badge>
                      ) : !source.enabled ? (
                        <Badge>{t("sources.paused")}</Badge>
                      ) : source.lastStatus === "FAILED" ? (
                        <Badge tone="bad">{label("pending", "SOURCE_FAILING")}</Badge>
                      ) : source.due ? (
                        <Badge tone="warn">{t("sources.due")}</Badge>
                      ) : (
                        <Badge tone="good">{source.lastStatus ?? "—"}</Badge>
                      )}
                      {source.lastMessage && <div className="muted">{source.lastMessage}</div>}
                      <div className="muted">
                        {t("sources.next")}: {formatDate(source.nextRunAt, true)}
                      </div>
                    </td>
                    <td className="row-actions">
                      {source.collectionMode === "AUTOMATIC" ? (
                        <Button
                          disabled={busy || source.running}
                          onClick={() =>
                            void run(async () => {
                              const { data } = await api.runSource(source.id);
                              return t("sources.ran", { message: data.message ?? data.status });
                            })
                          }
                        >
                          {t("sources.run")}
                        </Button>
                      ) : (
                        <Button
                          disabled={busy}
                          onClick={() =>
                            void run(async () => {
                              await api.checkSource(source.id, { jobsRegistered: 0, note: null });
                              return t("sources.checked");
                            })
                          }
                        >
                          {t("sources.check")}
                        </Button>
                      )}
                      <Button variant="ghost" onClick={() => void showHistory(source)}>
                        {t("sources.history")}
                      </Button>
                      <Button
                        variant="ghost"
                        onClick={() => {
                          if (window.confirm(t("common.confirmDelete"))) {
                            void run(async () => {
                              await api.deleteSource(source.id);
                              setSelected((current) => current.filter((id) => id !== source.id));
                              return t("common.saved");
                            });
                          }
                        }}
                      >
                        {t("common.delete")}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {history && (
        <Card title={`${t("sources.history")}: ${all.find((s) => s.id === history.sourceId)?.name ?? ""}`} actions={<Button onClick={() => setHistory(null)}>×</Button>}>
          {history.runs.length === 0 ? (
            <Empty />
          ) : (
            <ul className="timeline">
              {history.runs.map((item) => (
                <li key={item.id}>
                  <Badge tone={item.status === "FAILED" ? "bad" : "good"}>{item.status}</Badge> <span className="muted">{formatDate(item.startedAt, true)}</span>
                  <p>{item.message}</p>
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}
    </div>
  );
}
