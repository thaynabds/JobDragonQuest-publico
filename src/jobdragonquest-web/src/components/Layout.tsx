import { NavLink, Outlet } from "react-router";

import { useAuth } from "../auth/AuthContext";
import { useI18n } from "../i18n/I18nContext";
import { Bell } from "../notifications/Bell";
import { NotificationsProvider } from "../notifications/NotificationsContext";
import { Toasts } from "../notifications/Toasts";
import { LanguageSelect } from "./LanguageSelect";

const NAV = [
  { to: "/", key: "nav.dashboard" },
  { to: "/candidaturas", key: "nav.board" },
  { to: "/revisao", key: "nav.review" },
  { to: "/vagas", key: "nav.jobs" },
  { to: "/fontes", key: "nav.sources" },
  { to: "/perfis", key: "nav.profiles" },
  { to: "/carreira", key: "nav.career" },
  { to: "/configuracoes", key: "nav.settings" },
] as const;

export function Layout() {
  const { t } = useI18n();
  const { user, logout } = useAuth();
  return (
    <NotificationsProvider>
    <a className="skip-link" href="#conteudo">
      {t("a11y.skip")}
    </a>
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <img className="brand-logo" src="/logo.png" alt="" width="44" height="44" />
          <div>
            <strong>{t("app.name")}</strong>
            <span className="brand-tagline">{t("app.tagline")}</span>
          </div>
          <Bell />
        </div>
        <nav aria-label="JobDragonQuest">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === "/"} className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
              {t(item.key)}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <LanguageSelect />
          {user && <span className="user">{user.displayName}</span>}
          <button type="button" className="btn btn-ghost" onClick={() => void logout()}>
            {t("nav.logout")}
          </button>
        </div>
      </aside>
      <main className="content" id="conteudo" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
    <Toasts />
    </NotificationsProvider>
  );
}
