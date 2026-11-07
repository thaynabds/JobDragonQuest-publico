import { Link } from "react-router";

import { api } from "../api/endpoints";
import { Badge, Card, Empty, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";

export function ProfilesPage() {
  const { t, label, errorMessage } = useI18n();
  const profiles = useAsync(() => api.profiles().then((r) => r.data), []);

  return (
    <div className="page">
      <div className="page-header">
        <h1>{t("profiles.title")}</h1>
        <Link className="btn btn-primary" to="/perfis/novo">
          {t("profiles.new")}
        </Link>
      </div>
      <p className="muted">{t("profiles.truth")}</p>
      {profiles.error ? <Notice tone="error">{errorMessage(profiles.error)}</Notice> : null}
      {profiles.loading && !profiles.data ? <Loading /> : null}
      {profiles.data && profiles.data.length === 0 && <Empty />}
      <div className="grid">
        {profiles.data?.map((profile) => (
          <Card key={profile.id} title={<Link to={`/perfis/${profile.id}`}>{profile.name}</Link>}>
            <p>
              <Badge tone="info">{label("seniority", profile.seniority)}</Badge>{" "}
              <span className="muted">{t("profiles.skillCount", { count: profile.skillCount })}</span>
            </p>
            {profile.headline && <p>{profile.headline}</p>}
            {profile.desiredTitles.length > 0 && <p className="muted">{profile.desiredTitles.join(" · ")}</p>}
            <Link to={`/perfis/${profile.id}/curriculo`}>{t("resume.open")}</Link>
          </Card>
        ))}
      </div>
    </div>
  );
}
