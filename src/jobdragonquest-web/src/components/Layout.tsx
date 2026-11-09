import { useEffect, useState } from "react";
import { UpdateNotice } from "./UpdateNotice";
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
  { to: "/assistentes", key: "nav.assistants" },
  { to: "/triagem", key: "nav.screening" },
  { to: "/concursos", key: "nav.contests" },
  { to: "/ajuda", key: "nav.help" },
  { to: "/configuracoes", key: "nav.settings" },
] as const;

export function Layout() {
  const { t } = useI18n();
  const { user, logout } = useAuth();
  // O menu fica recolhido e abre de qualquer página pelo botão da barra superior.
  const [menuOpen, setMenuOpen] = useState(false);
  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setMenuOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [menuOpen]);
  return (
    <NotificationsProvider>
    <a className="skip-link" href="#conteudo">
      {t("a11y.skip")}
    </a>
    <div className="shell">
      <header className="topbar">
        <button type="button" className="btn btn-ghost" aria-expanded={menuOpen} aria-controls="menu-principal" onClick={() => setMenuOpen(!menuOpen)}>
          ☰ {t("nav.menu")}
        </button>
        <strong className="topbar-name">{t("app.name")}</strong>
        <Bell />
      </header>
      {menuOpen && <div className="menu-backdrop" onClick={() => setMenuOpen(false)} aria-hidden="true" />}
      <aside id="menu-principal" className={menuOpen ? "sidebar open" : "sidebar"} inert={!menuOpen}>
        <div className="brand">
          <img className="brand-logo" src="/logo.png" alt="" width="44" height="44" />
          <div>
            <strong>{t("app.name")}</strong>
            <span className="brand-tagline">{t("app.tagline")}</span>
          </div>
        </div>
        <nav aria-label="JobDragonQuest">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === "/"} onClick={() => setMenuOpen(false)} className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
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
        <UpdateNotice />
        <Outlet />
      </main>
    </div>
    <Toasts />
    </NotificationsProvider>
  );
}
