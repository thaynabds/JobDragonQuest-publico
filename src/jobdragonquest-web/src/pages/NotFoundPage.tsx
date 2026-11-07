import { Link } from "react-router";

import { useI18n } from "../i18n/I18nContext";

export function NotFoundPage() {
  const { t } = useI18n();
  return (
    <div className="page">
      <h1>404</h1>
      <p className="muted">{t("error.not_found")}</p>
      <Link to="/">{t("nav.dashboard")}</Link>
    </div>
  );
}
