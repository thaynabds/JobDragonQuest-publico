import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

import { onTokenChange, refreshSession, setAccessToken } from "../api/client";
import { api } from "../api/endpoints";
import type { Me } from "../api/types";

interface Auth {
  user: Me | null;
  ready: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (displayName: string, email: string, password: string, locale: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<Auth | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [ready, setReady] = useState(false);

  const loadMe = useCallback(async () => {
    const { data } = await api.me();
    setUser(data);
  }, []);

  // Ao abrir o painel, tenta retomar a sessão pelo cookie HttpOnly.
  useEffect(() => {
    let active = true;
    refreshSession()
      .then(async (token) => {
        if (token && active) {
          await loadMe();
        }
      })
      .catch(() => setUser(null))
      .finally(() => active && setReady(true));
    const unsubscribe = onTokenChange((token) => {
      if (!token) {
        setUser(null);
      }
    });
    return () => {
      active = false;
      unsubscribe();
    };
  }, [loadMe]);

  const value = useMemo<Auth>(
    () => ({
      user,
      ready,
      login: async (email, password) => {
        const { data } = await api.login({ email, password });
        setAccessToken(data.accessToken);
        await loadMe();
      },
      register: async (displayName, email, password, locale) => {
        const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || "America/Recife";
        const { data } = await api.register({ displayName, email, password, locale, timeZone });
        setAccessToken(data.accessToken);
        await loadMe();
      },
      logout: async () => {
        try {
          await api.logout();
        } finally {
          setAccessToken(null);
          setUser(null);
        }
      },
    }),
    [user, ready, loadMe],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): Auth {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth fora do AuthProvider");
  }
  return context;
}
