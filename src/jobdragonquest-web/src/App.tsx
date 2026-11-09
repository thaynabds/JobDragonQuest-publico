import { AssistantsPage } from "./pages/AssistantsPage";
import { ContestsPage } from "./pages/ContestsPage";
import { HelpPage } from "./pages/HelpPage";
import { ScreeningPage } from "./pages/ScreeningPage";
import { BrowserRouter, Route, Routes } from "react-router";

import { AccessAbilityWidget } from "@thaynabds/accessability-widget/react";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { Layout } from "./components/Layout";
import { Loading } from "./components/ui";
import { I18nProvider } from "./i18n/I18nContext";
import { ApplicationDetailPage } from "./pages/ApplicationDetailPage";
import { BoardPage } from "./pages/BoardPage";
import { DashboardPage } from "./pages/DashboardPage";
import { JobDetailPage } from "./pages/JobDetailPage";
import { JobsPage } from "./pages/JobsPage";
import { LoginPage } from "./pages/LoginPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { ProfileEditorPage } from "./pages/ProfileEditorPage";
import { ProfilesPage } from "./pages/ProfilesPage";
import { SettingsPage } from "./pages/SettingsPage";
import { SourcesPage } from "./pages/SourcesPage";
import { ReviewQueuePage } from "./pages/ReviewQueuePage";
import { CareerPage } from "./pages/CareerPage";
import { ResumePage } from "./pages/ResumePage";

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
        <Route path="candidaturas" element={<BoardPage />} />
        <Route path="revisao" element={<ReviewQueuePage />} />
        <Route path="candidaturas/:id" element={<ApplicationDetailPage />} />
        <Route path="vagas" element={<JobsPage />} />
        <Route path="vagas/:id" element={<JobDetailPage />} />
        <Route path="fontes" element={<SourcesPage />} />
        <Route path="perfis" element={<ProfilesPage />} />
        <Route path="perfis/:id" element={<ProfileEditorPage />} />
        <Route path="perfis/:id/curriculo" element={<ResumePage />} />
        <Route path="carreira" element={<CareerPage />} />
        <Route path="assistentes" element={<AssistantsPage />} />
        <Route path="triagem" element={<ScreeningPage />} />
        <Route path="concursos" element={<ContestsPage />} />
        <Route path="ajuda" element={<HelpPage />} />
        <Route path="configuracoes" element={<SettingsPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export function App() {
  return (
    <I18nProvider>
      <>
        <AuthProvider>
          <BrowserRouter>
            <Routed />
          </BrowserRouter>
        </AuthProvider>
        <AccessAbilityWidget />
      </>
    </I18nProvider>
  );
}
