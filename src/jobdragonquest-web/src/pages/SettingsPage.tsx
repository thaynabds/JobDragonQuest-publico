import { useI18n } from "../i18n/I18nContext";
import { AccountSection } from "./AccountSection";
import { NotificationSettingsSection } from "./NotificationSettingsSection";
import { TokensSection } from "./TokensSection";

export function SettingsPage() {
  const { t } = useI18n();
  return (
    <div className="page">
      <div className="page-header">
        <h1>{t("settings.title")}</h1>
      </div>
      <AccountSection />
      <NotificationSettingsSection />
      <TokensSection />
    </div>
  );
}
