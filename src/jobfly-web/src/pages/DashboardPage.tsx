import { api } from "../api/endpoints";
import { Card, Loading, Notice, Stat } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";
import { PendingList } from "./PendingList";

export function DashboardPage() {
  const { t, errorMessage } = useI18n();
  const summary = useAsync(() => api.summary().then((r) => r.data), []);

  return (
    <div className="page">
      <h1>{t("dashboard.title")}</h1>
      {summary.error ? <Notice tone="error">{errorMessage(summary.error)}</Notice> : null}
      {summary.loading && !summary.data ? <Loading /> : null}
      {summary.data && (
        <div className="stats">
          <Stat label={t("dashboard.jobsFound")} value={summary.data.jobsFound} />
          <Stat label={t("dashboard.applications")} value={summary.data.applicationsTotal} />
          <Stat label={t("dashboard.submitted")} value={summary.data.submitted} />
          <Stat label={t("dashboard.inProgress")} value={summary.data.inProgress} />
          <Stat label={t("dashboard.interviews")} value={summary.data.interviewsScheduled} tone="good" />
          <Stat label={t("dashboard.tests")} value={summary.data.testsPending} tone="warn" />
          <Stat label={t("dashboard.offers")} value={summary.data.offers} tone="good" />
          <Stat label={t("dashboard.approved")} value={summary.data.approved} tone="good" />
          <Stat label={t("dashboard.rejected")} value={summary.data.rejected} tone="bad" />
          <Stat label={t("dashboard.stale")} value={summary.data.stale} tone="warn" />
          <Stat label={t("dashboard.sources")} value={summary.data.sourcesWatched} />
          <Stat label={t("dashboard.sourcesToCheck")} value={summary.data.sourcesToCheck} tone="warn" />
        </div>
      )}
      <Card title={t("dashboard.pending")}>
        <PendingList onChange={summary.reload} />
      </Card>
    </div>
  );
}
