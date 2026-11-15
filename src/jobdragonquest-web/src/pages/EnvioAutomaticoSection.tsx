import { useEffect, useState } from "react";

import { api } from "../api/endpoints";
import type { EnvioAutomatico } from "../api/types";
import { Button, Card, Loading, Notice } from "../components/ui";
import { useI18n } from "../i18n/I18nContext";

/**
 * Liga ou desliga o envio automático da extensão (issue #246). A tela só espelha o estado: o servidor confere o aviso
 * aceito e o aprendizado (10 envios confirmados com um clique) em cada chamada.
 */
export function EnvioAutomaticoSection() {
  const { t, errorMessage } = useI18n();
  const [estado, setEstado] = useState<EnvioAutomatico | null>(null);
  const [aceito, setAceito] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: "error" | "success"; text: string } | null>(null);

  useEffect(() => {
    api
      .envioAutomatico()
      .then((response) => setEstado(response.data))
      .catch((error: unknown) => setMessage({ tone: "error", text: errorMessage(error) }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const alterar = async (ativo: boolean) => {
    setBusy(true);
    setMessage(null);
    try {
      const response = await api.definirEnvioAutomatico({ ativo, avisoAceito: aceito });
      setEstado(response.data);
      setAceito(false);
      setMessage({ tone: "success", text: t("common.saved") });
    } catch (error) {
      setMessage({ tone: "error", text: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  if (!estado) {
    return <Card title={t("auto.title")}>{message ? <Notice tone="error">{message.text}</Notice> : <Loading />}</Card>;
  }

  return (
    <Card title={t("auto.title")}>
      <p>{t("auto.progress", { confirmed: estado.confirmados, limit: estado.limite })}</p>
      {message && <Notice tone={message.tone}>{message.text}</Notice>}
      {estado.ativo ? (
        <>
          <p>{t("auto.on")}</p>
          <Button disabled={busy} onClick={() => void alterar(false)}>
            {t("auto.disable")}
          </Button>
        </>
      ) : !estado.liberado ? (
        <p>{t("auto.locked")}</p>
      ) : (
        <>
          <p>{t("auto.off")}</p>
          <Notice tone="info">{t("auto.warning")}</Notice>
          <label className="check">
            <input type="checkbox" checked={aceito} onChange={(event) => setAceito(event.target.checked)} /> {t("auto.accept")}
          </label>
          <Button variant="primary" disabled={busy || !aceito} onClick={() => void alterar(true)}>
            {t("auto.enable")}
          </Button>
        </>
      )}
      <p className="nota">{t("auto.note")}</p>
    </Card>
  );
}
