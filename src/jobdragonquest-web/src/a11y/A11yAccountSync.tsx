import { useEffect, useRef } from "react";

import { api } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { useA11y } from "./A11yContext";
import { sanitize } from "./prefs";

/**
 * Leva as preferências de acessibilidade para a conta (issue #126). No login, as da conta valem (e, se a conta ainda
 * não tem, as deste navegador são salvas nela). Depois, cada mudança é salva na conta, agrupada em 800 ms. Os valores
 * recebidos passam pelo mesmo sanitize do armazenamento local.
 */
export function A11yAccountSync() {
  const { user } = useAuth();
  const { prefs, update } = useA11y();
  const loadedFor = useRef<string | null>(null);
  const skipSave = useRef(false);

  useEffect(() => {
    if (!user) {
      loadedFor.current = null;
      return;
    }
    if (loadedFor.current === user.id) {
      return;
    }
    let cancelled = false;
    api
      .accessibility()
      .then(({ data }) => {
        if (cancelled) return;
        if (data) {
          skipSave.current = true;
          update(sanitize(data));
        } else {
          void api.saveAccessibility(prefs).catch(() => undefined);
        }
      })
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) loadedFor.current = user.id;
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  useEffect(() => {
    if (!user || loadedFor.current !== user.id) {
      return;
    }
    if (skipSave.current) {
      skipSave.current = false;
      return;
    }
    const timer = setTimeout(() => void api.saveAccessibility(prefs).catch(() => undefined), 800);
    return () => clearTimeout(timer);
  }, [prefs, user]);

  return null;
}
