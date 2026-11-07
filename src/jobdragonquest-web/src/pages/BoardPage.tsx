import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";

import { api } from "../api/endpoints";
import { APPLICATION_STATES, ORIGINS, type ApplicationState, type CreateApplicationInput, type Origin } from "../api/types";
import { Badge, Button, Card, Field, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { localInputToIso, newIdempotencyKey } from "../utils/safe";
import { useAsync } from "../utils/useAsync";

const blank = (value: string) => (value.trim() === "" ? null : value.trim());

export function BoardPage() {
  const { t, label, formatDate, errorMessage } = useI18n();
  const navigate = useNavigate();
  const [filters, setFilters] = useState({ profileId: "", platform: "", company: "", country: "" });
  const [applied, setApplied] = useState(filters);
  const board = useAsync(
    () =>
      api
        .board({
          profileId: applied.profileId || undefined,
          platform: applied.platform || undefined,
          company: applied.company || undefined,
          country: applied.country || undefined,
        })
        .then((r) => r.data),
    [applied],
  );
  const profiles = useAsync(() => api.profiles().then((r) => r.data), []);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<CreateApplicationInput & { appliedLocal: string }>({
    origin: "MANUAL",
    platform: "LinkedIn",
    company: "",
    title: "",
    url: "",
    country: "",
    state: "SUBMITTED",
    notes: "",
    profileId: "",
    appliedLocal: "",
  });
  const [idempotencyKey, setIdempotencyKey] = useState(() => newIdempotencyKey("ext"));
  const [notice, setNotice] = useState<{ tone: "error" | "success"; text: string } | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setNotice(null);
    try {
      const response = await api.createApplication(
        {
          origin: form.origin,
          platform: blank(form.platform ?? ""),
          company: blank(form.company ?? ""),
          title: blank(form.title ?? ""),
          url: blank(form.url ?? ""),
          country: blank((form.country ?? "").toUpperCase()),
          appliedAt: localInputToIso(form.appliedLocal),
          state: form.state ?? null,
          notes: blank(form.notes ?? ""),
          profileId: form.profileId || null,
        },
        idempotencyKey,
      );
      setIdempotencyKey(newIdempotencyKey("ext"));
      const duplicate = response.headers.get("X-Duplicate-Of");
      setNotice({ tone: "success", text: duplicate ? t("application.duplicate") : t("application.created") });
      navigate(`/candidaturas/${response.data.id}`);
    } catch (err) {
      setNotice({ tone: "error", text: errorMessage(err) });
    }
  };

  const columns = (board.data ?? []).filter((column) => column.count > 0 || ["SUBMITTED", "INTERVIEW_SCHEDULED"].includes(column.state));

  return (
    <div className="page page-wide">
      <div className="page-header">
        <h1>{t("board.title")}</h1>
        <Button variant="primary" onClick={() => setShowForm(!showForm)}>
          {t("board.new")}
        </Button>
      </div>
      {notice && (
        <Notice tone={notice.tone} onClose={() => setNotice(null)}>
          {notice.text}
        </Notice>
      )}

      {showForm && (
        <Card title={t("board.new")}>
          <form className="form-grid" onSubmit={(event) => void submit(event)}>
            <Field label={t("board.company")}>
              <input value={form.company ?? ""} required maxLength={200} onChange={(e) => setForm({ ...form, company: e.target.value })} />
            </Field>
            <Field label={t("application.title")}>
              <input value={form.title ?? ""} required maxLength={200} onChange={(e) => setForm({ ...form, title: e.target.value })} />
            </Field>
            <Field label={t("application.origin")}>
              <select value={form.origin} onChange={(e) => setForm({ ...form, origin: e.target.value as Origin })}>
                {ORIGINS.filter((origin) => origin !== "AUTOMATION").map((origin) => (
                  <option key={origin} value={origin}>
                    {label("origin", origin)}
                  </option>
                ))}
              </select>
            </Field>
            <Field label={t("board.platform")}>
              <input
                value={form.platform ?? ""}
                maxLength={80}
                list="platforms"
                onChange={(e) => setForm({ ...form, platform: e.target.value })}
              />
              <datalist id="platforms">
                {["LinkedIn", "Gupy", "Indeed", "Site da empresa", "Instagram", "Facebook", "E-mail", "Indicação", "Workday", "Greenhouse"].map((p) => (
                  <option key={p} value={p} />
                ))}
              </datalist>
            </Field>
            <Field label={t("application.url")} wide>
              <input type="url" value={form.url ?? ""} maxLength={2048} onChange={(e) => setForm({ ...form, url: e.target.value })} />
            </Field>
            <Field label={t("application.appliedAt")}>
              <input type="datetime-local" value={form.appliedLocal} onChange={(e) => setForm({ ...form, appliedLocal: e.target.value })} />
            </Field>
            <Field label={t("application.state")}>
              <select value={form.state ?? "SUBMITTED"} onChange={(e) => setForm({ ...form, state: e.target.value as ApplicationState })}>
                {APPLICATION_STATES.map((state) => (
                  <option key={state} value={state}>
                    {label("state", state)}
                  </option>
                ))}
              </select>
            </Field>
            <Field label={t("board.profile")}>
              <select value={form.profileId ?? ""} onChange={(e) => setForm({ ...form, profileId: e.target.value })}>
                <option value="">{t("common.none")}</option>
                {(profiles.data ?? []).map((profile) => (
                  <option key={profile.id} value={profile.id}>
                    {profile.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label={t("board.country")}>
              <input value={form.country ?? ""} maxLength={2} onChange={(e) => setForm({ ...form, country: e.target.value })} />
            </Field>
            <Field label={t("application.notes")} wide>
              <textarea rows={2} maxLength={4000} value={form.notes ?? ""} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
            </Field>
            <div className="field">
              <Button type="submit" variant="primary">
                {t("common.save")}
              </Button>
            </div>
          </form>
        </Card>
      )}

      <form
        className="filters"
        onSubmit={(event) => {
          event.preventDefault();
          setApplied(filters);
        }}
      >
        <select aria-label={t("board.profile")} value={filters.profileId} onChange={(e) => setFilters({ ...filters, profileId: e.target.value })}>
          <option value="">
            {t("board.profile")}: {t("common.all")}
          </option>
          {(profiles.data ?? []).map((profile) => (
            <option key={profile.id} value={profile.id}>
              {profile.name}
            </option>
          ))}
        </select>
        <input placeholder={t("board.company")} value={filters.company} onChange={(e) => setFilters({ ...filters, company: e.target.value })} />
        <input placeholder={t("board.platform")} value={filters.platform} onChange={(e) => setFilters({ ...filters, platform: e.target.value })} />
        <input placeholder={t("board.country")} maxLength={2} value={filters.country} onChange={(e) => setFilters({ ...filters, country: e.target.value })} />
        <Button type="submit">{t("common.filter")}</Button>
      </form>

      {board.error ? <Notice tone="error">{errorMessage(board.error)}</Notice> : null}
      {board.loading && !board.data ? <Loading /> : null}
      <div className="board">
        {columns.map((column) => (
          <section key={column.state} className="column" aria-label={label("state", column.state)}>
            <header className="column-header">
              <span>{label("state", column.state)}</span>
              <Badge>{column.count}</Badge>
            </header>
            {column.cards.length === 0 && <p className="muted small">{t("board.empty")}</p>}
            {column.cards.map((card) => (
              <Link key={card.id} to={`/candidaturas/${card.id}`} className="kanban-card">
                <strong>{card.title}</strong>
                <span>{card.company}</span>
                <span className="small muted">
                  {card.platform ?? label("origin", card.origin)} · {formatDate(card.appliedAt ?? card.createdAt)}
                </span>
                <span className="card-badges">
                  {card.matchScore !== null && <Badge tone="info">{card.matchScore}%</Badge>}
                  {card.stale && <Badge tone="warn">{t("board.stale")}</Badge>}
                </span>
                {card.nextAction && <span className="small">→ {card.nextAction}</span>}
              </Link>
            ))}
          </section>
        ))}
      </div>
    </div>
  );
}
