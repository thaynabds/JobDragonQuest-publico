import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

import "@fontsource/opendyslexic/400.css";
import "@fontsource/opendyslexic/700.css";
import "./a11y.css";

import { A11yFilters } from "./filters";
import { ALL_CLASSES, DEFAULT_PREFS, documentState, loadPrefs, savePrefs, type A11yPrefs } from "./prefs";

interface A11y {
  prefs: A11yPrefs;
  update: (change: Partial<A11yPrefs>) => void;
  reset: () => void;
  open: boolean;
  setOpen: (open: boolean) => void;
  speaking: boolean;
  /** Lê em voz alta o texto selecionado ou, sem seleção, o conteúdo principal da página. */
  speak: (lang: string) => void;
  stopSpeaking: () => void;
}

const A11yContext = createContext<A11y | null>(null);
const MAX_SPEECH = 6000;

/** Aplica as preferências no <html>: atributo de tema, escala da fonte e classes de lista fixa. */
export function applyToDocument(prefs: A11yPrefs, root: HTMLElement = document.documentElement): void {
  const state = documentState(prefs);
  root.classList.remove(...ALL_CLASSES);
  root.classList.add(...state.classes);
  if (state.theme === "system") {
    root.removeAttribute("data-theme");
  } else {
    root.setAttribute("data-theme", state.theme);
  }
  root.setAttribute("data-font-scale", String(state.fontScale));
}

function pauseAllVideos(scope: ParentNode): void {
  scope.querySelectorAll("video").forEach((video) => {
    try {
      video.pause();
    } catch {
      // Vídeo sem suporte a pausa: ignora.
    }
  });
}

export function A11yProvider({ children, initial }: { children: ReactNode; initial?: A11yPrefs }) {
  const [prefs, setPrefs] = useState<A11yPrefs>(initial ?? loadPrefs);
  const [open, setOpen] = useState(false);
  const [speaking, setSpeaking] = useState(false);

  useEffect(() => {
    applyToDocument(prefs);
    savePrefs(prefs);
  }, [prefs]);

  // Alt+A abre e fecha o painel de qualquer lugar (atalho do a11y-widget).
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.altKey && !event.ctrlKey && !event.metaKey && event.key.toLowerCase() === "a") {
        event.preventDefault();
        setOpen((value) => !value);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  // Pausar vídeos: pausa os atuais e os que aparecerem depois.
  useEffect(() => {
    if (!prefs.pauseVideos) {
      return;
    }
    pauseAllVideos(document);
    const observer = new MutationObserver(() => pauseAllVideos(document));
    observer.observe(document.body, { childList: true, subtree: true });
    const onPlay = (event: Event) => {
      if (event.target instanceof HTMLVideoElement) {
        event.target.pause();
      }
    };
    document.addEventListener("play", onPlay, true);
    return () => {
      observer.disconnect();
      document.removeEventListener("play", onPlay, true);
    };
  }, [prefs.pauseVideos]);

  const update = useCallback((change: Partial<A11yPrefs>) => setPrefs((current) => ({ ...current, ...change })), []);
  const reset = useCallback(() => setPrefs(DEFAULT_PREFS), []);

  const stopSpeaking = useCallback(() => {
    if ("speechSynthesis" in window) {
      window.speechSynthesis.cancel();
    }
    setSpeaking(false);
  }, []);

  const speak = useCallback((lang: string) => {
    if (!("speechSynthesis" in window)) {
      return;
    }
    const selected = window.getSelection()?.toString().trim();
    const main = document.querySelector("main")?.textContent ?? document.body.textContent ?? "";
    const text = (selected || main).replace(/\s+/g, " ").trim().slice(0, MAX_SPEECH);
    if (!text) {
      return;
    }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = lang;
    utterance.onend = () => setSpeaking(false);
    utterance.onerror = () => setSpeaking(false);
    setSpeaking(true);
    window.speechSynthesis.speak(utterance);
  }, []);

  useEffect(() => stopSpeaking, [stopSpeaking]);

  const value = useMemo<A11y>(
    () => ({ prefs, update, reset, open, setOpen, speaking, speak, stopSpeaking }),
    [prefs, update, reset, open, speaking, speak, stopSpeaking],
  );

  return (
    <A11yContext.Provider value={value}>
      <A11yFilters />
      {children}
    </A11yContext.Provider>
  );
}

export function useA11y(): A11y {
  const context = useContext(A11yContext);
  if (!context) {
    throw new Error("useA11y fora do A11yProvider");
  }
  return context;
}
