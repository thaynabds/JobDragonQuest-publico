import { BrowserRouter, Route, Routes } from "react-router";

import { AuthProvider, useAuth } from "./auth/AuthContext";
import { Layout } from "./components/Layout";
import { Loading } from "./components/ui";
import { I18nProvider } from "./i18n/I18nContext";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { ProfileEditorPage } from "./pages/ProfileEditorPage";
import { ProfilesPage } from "./pages/ProfilesPage";

function Routed() {
  const { user, ready } = useAuth();
  if (!ready) {
    return <Loading />;
  }
  if (!user) {
    return <LoginPage />;
  }
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="perfis" element={<ProfilesPage />} />
        <Route path="perfis/:id" element={<ProfileEditorPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export function App() {
  return (
    <I18nProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routed />
        </BrowserRouter>
      </AuthProvider>
    </I18nProvider>
  );
}
