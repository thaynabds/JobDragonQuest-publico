import { api } from "../api/endpoints";
import { Card, Loading, Notice, Stat } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";
import { PendingList } from "./PendingList";
import { QuickActions } from "./QuickActions";

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
          <Stat to="/vagas" info={t("dashboard.help.jobsFound")} label={t("dashboard.jobsFound")} value={summary.data.jobsFound} />
          <Stat to="/candidaturas" info={t("dashboard.help.applications")} label={t("dashboard.applications")} value={summary.data.applicationsTotal} />
          <Stat to="/candidaturas" info={t("dashboard.help.submitted")} label={t("dashboard.submitted")} value={summary.data.submitted} />
          <Stat to="/candidaturas" info={t("dashboard.help.inProgress")} label={t("dashboard.inProgress")} value={summary.data.inProgress} />
          <Stat to="/candidaturas" info={t("dashboard.help.interviews")} label={t("dashboard.interviews")} value={summary.data.interviewsScheduled} tone="good" />
          <Stat to="/candidaturas" info={t("dashboard.help.tests")} label={t("dashboard.tests")} value={summary.data.testsPending} tone="warn" />
          <Stat to="/candidaturas" info={t("dashboard.help.offers")} label={t("dashboard.offers")} value={summary.data.offers} tone="good" />
          <Stat to="/candidaturas" info={t("dashboard.help.approved")} label={t("dashboard.approved")} value={summary.data.approved} tone="good" />
          <Stat to="/candidaturas" info={t("dashboard.help.rejected")} label={t("dashboard.rejected")} value={summary.data.rejected} tone="bad" />
          <Stat to="/candidaturas" info={t("dashboard.help.stale")} label={t("dashboard.stale")} value={summary.data.stale} tone="warn" />
          <Stat to="/fontes" info={t("dashboard.help.sources")} label={t("dashboard.sources")} value={summary.data.sourcesWatched} />
          <Stat to="/fontes" info={t("dashboard.help.sourcesToCheck")} label={t("dashboard.sourcesToCheck")} value={summary.data.sourcesToCheck} tone="warn" />
        </div>
      )}
      <Card title={t("dashboard.pending")}>
        <PendingList onChange={summary.reload} />
      </Card>
      <Card title={t("quick.title")}>
        <QuickActions />
      </Card>
    </div>
  );
}
