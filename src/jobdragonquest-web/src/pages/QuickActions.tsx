import { Link } from "react-router";

import { useI18n } from "../i18n/I18nContext";
import type { MessageKey } from "../i18n/messages";

/** Tudo o que dá para fazer no painel sem precisar da IA, com atalho para a tela certa (#223). */
export const QUICK_ACTIONS = [
  { id: "job", to: "/vagas" },
  { id: "application", to: "/candidaturas" },
  { id: "sources", to: "/fontes" },
  { id: "profile", to: "/perfis" },
  { id: "prepare", to: "/vagas" },
  { id: "chat", to: "/assistentes" },
  { id: "career", to: "/carreira" },
  { id: "screening", to: "/triagem" },
  { id: "contests", to: "/concursos" },
  { id: "review", to: "/revisao" },
] as const;

export function QuickActions() {
  const { t } = useI18n();
  return (
    <ul className="quick-actions">
      {QUICK_ACTIONS.map((action) => (
        <li key={action.id}>
          <Link to={action.to}>
            <strong>{t(`quick.${action.id}.title` as MessageKey)}</strong>
            <span className="muted small">{t(`quick.${action.id}.text` as MessageKey)}</span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
