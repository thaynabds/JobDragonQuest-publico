import { NavLink, Outlet } from "react-router";

import { useAuth } from "../auth/AuthContext";
import { useI18n } from "../i18n/I18nContext";
import type { Locale } from "../i18n/messages";

const NAV = [
  { to: "/", key: "nav.dashboard" },
  { to: "/candidaturas", key: "nav.board" },
  { to: "/vagas", key: "nav.jobs" },
  { to: "/fontes", key: "nav.sources" },
  { to: "/perfis", key: "nav.profiles" },
] as const;

export function Layout() {
  const { t, locale, setLocale } = useI18n();
  const { user, logout } = useAuth();
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <img className="brand-logo" src="/logo.png" alt="" width="44" height="44" />
          <div>
            <strong>{t("app.name")}</strong>
            <span className="brand-tagline">{t("app.tagline")}</span>
          </div>
        </div>
        <nav aria-label="JobFly">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === "/"} className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
              {t(item.key)}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <label className="lang">
            <span>{t("lang.label")}</span>
            <select value={locale} onChange={(event) => setLocale(event.target.value as Locale)}>
              <option value="pt-BR">Português</option>
              <option value="en">English</option>
            </select>
          </label>
          {user && <span className="user">{user.displayName}</span>}
          <button type="button" className="btn btn-ghost" onClick={() => void logout()}>
            {t("nav.logout")}
          </button>
        </div>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
