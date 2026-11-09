import { Card } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";
import type { MessageKey } from "../i18n/messages";

const TOPICS = ["fewJobs", "irrelevant", "countries", "closed", "submitted", "interviews", "tailored"] as const;

/** Central de ajuda: tudo é gratuito, então não há assinatura nem cobrança para resolver aqui. */
export function HelpPage() {
  const { t } = useI18n();
  return (
    <>
      <div className="page-header"><h1>{t("help.title")}</h1></div>
      {TOPICS.map((topic) => (
        <Card key={topic} title={t(`help.topic.${topic}` as MessageKey)}>
          <p>{t(`help.answer.${topic}` as MessageKey)}</p>
        </Card>
      ))}
      <Card title={t("help.more")}>
        <p><a href="https://github.com/thaynabds/JobDragonQuest-publico/issues" target="_blank" rel="noopener noreferrer">{t("help.issues")}</a></p>
      </Card>
    </>
  );
}
