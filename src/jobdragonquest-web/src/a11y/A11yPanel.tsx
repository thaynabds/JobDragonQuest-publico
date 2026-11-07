import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";

import { useI18n } from "../i18n/I18nContext";
import type { MessageKey } from "../i18n/messages";
import { useA11y } from "./A11yContext";
import { COLOR_FILTERS, FONT_SCALES, THEMES, TOGGLES, type ColorFilter, type Theme } from "./prefs";

const POSITION_KEY = "jobdragonquest.a11y.button";

interface Position {
  right: number;
  bottom: number;
}

function loadPosition(): Position {
  try {
    const raw = JSON.parse(window.localStorage.getItem(POSITION_KEY) ?? "null") as Partial<Position> | null;
    const right = Number(raw?.right);
    const bottom = Number(raw?.bottom);
    if (Number.isFinite(right) && Number.isFinite(bottom)) {
      return clamp({ right, bottom });
    }
  } catch {
    // Sem posição salva: canto inferior direito.
  }
  return { right: 16, bottom: 16 };
}

function clamp(position: Position): Position {
  const maxRight = Math.max(0, window.innerWidth - 56);
  const maxBottom = Math.max(0, window.innerHeight - 56);
  return { right: Math.min(Math.max(0, position.right), maxRight), bottom: Math.min(Math.max(0, position.bottom), maxBottom) };
}

/** Faixa de leitura que acompanha o ponteiro (guia de leitura do a11y-widget). */
function ReadingGuide() {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const move = (event: PointerEvent) => {
      if (ref.current) {
        ref.current.style.top = `${Math.max(0, event.clientY - 24)}px`;
      }
    };
    window.addEventListener("pointermove", move);
    return () => window.removeEventListener("pointermove", move);
  }, []);
  return <div ref={ref} className="a11y-reading-guide-band" aria-hidden="true" />;
}

/** Botão flutuante arrastável (posição lembrada) e painel de acessibilidade. Atalho: Alt+A. */
export function A11yPanel() {
  const { t, locale } = useI18n();
  const { prefs, update, reset, open, setOpen, speaking, speak, stopSpeaking } = useA11y();
  const [position, setPosition] = useState<Position>(loadPosition);
  const drag = useRef<{ x: number; y: number; start: Position; moved: boolean } | null>(null);
  const button = useRef<HTMLButtonElement>(null);
  const dialog = useRef<HTMLDivElement>(null);

  // Posição pelo CSSOM (a CSP do build não permite atributo style no HTML).
  useEffect(() => {
    if (button.current) {
      button.current.style.right = `${position.right}px`;
      button.current.style.bottom = `${position.bottom}px`;
    }
  }, [position]);

  useEffect(() => {
    if (open) {
      dialog.current?.querySelector<HTMLElement>("select, button, input")?.focus();
    }
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
        button.current?.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, setOpen]);

  const onPointerDown = (event: ReactPointerEvent<HTMLButtonElement>) => {
    drag.current = { x: event.clientX, y: event.clientY, start: position, moved: false };
    event.currentTarget.setPointerCapture?.(event.pointerId);
  };
  const onPointerMove = (event: ReactPointerEvent<HTMLButtonElement>) => {
    const current = drag.current;
    if (!current) {
      return;
    }
    const dx = event.clientX - current.x;
    const dy = event.clientY - current.y;
    if (Math.abs(dx) + Math.abs(dy) > 4) {
      current.moved = true;
      setPosition(clamp({ right: current.start.right - dx, bottom: current.start.bottom - dy }));
    }
  };
  const onPointerUp = () => {
    const current = drag.current;
    drag.current = null;
    if (current?.moved) {
      try {
        window.localStorage.setItem(POSITION_KEY, JSON.stringify(position));
      } catch {
        // Posição só não fica salva.
      }
    } else {
      setOpen(!open);
    }
  };

  const scaleIndex = FONT_SCALES.indexOf(prefs.fontScale);

  return (
    <>
      {prefs.readingGuide && <ReadingGuide />}
      <button
        ref={button}
        type="button"
        className="a11y-button"
        aria-label={t("a11y.open")}
        aria-expanded={open}
        aria-controls="a11y-panel"
        title={`${t("a11y.open")} (Alt+A)`}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onKeyDown={(event) => {
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            setOpen(!open);
          }
        }}
      >
        <span aria-hidden="true">♿</span>
      </button>
      {open && (
        <div ref={dialog} id="a11y-panel" className="a11y-panel" role="dialog" aria-modal="false" aria-labelledby="a11y-title">
          <header className="a11y-panel-header">
            <h2 id="a11y-title">{t("a11y.title")}</h2>
            <button type="button" className="btn btn-ghost" onClick={() => setOpen(false)} aria-label={t("a11y.close")}>
              ×
            </button>
          </header>
          <label className="field">
            <span className="field-label">{t("a11y.theme")}</span>
            <select value={prefs.theme} onChange={(event) => update({ theme: event.target.value as Theme })}>
              {THEMES.map((theme) => (
                <option key={theme} value={theme}>
                  {t(`a11y.theme.${theme}` as MessageKey)}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="field-label">{t("a11y.filter")}</span>
            <select value={prefs.colorFilter} onChange={(event) => update({ colorFilter: event.target.value as ColorFilter })}>
              {COLOR_FILTERS.map((filter) => (
                <option key={filter} value={filter}>
                  {t(`a11y.filter.${filter}` as MessageKey)}
                </option>
              ))}
            </select>
          </label>
          <div className="field">
            <span className="field-label" id="a11y-font-label">
              {t("a11y.fontSize")}
            </span>
            <div className="a11y-stepper" role="group" aria-labelledby="a11y-font-label">
              <button
                type="button"
                className="btn"
                aria-label={t("a11y.fontSmaller")}
                disabled={scaleIndex <= 0}
                onClick={() => update({ fontScale: FONT_SCALES[Math.max(0, scaleIndex - 1)] })}
              >
                A−
              </button>
              <output aria-live="polite">{prefs.fontScale}%</output>
              <button
                type="button"
                className="btn"
                aria-label={t("a11y.fontBigger")}
                disabled={scaleIndex >= FONT_SCALES.length - 1}
                onClick={() => update({ fontScale: FONT_SCALES[Math.min(FONT_SCALES.length - 1, scaleIndex + 1)] })}
              >
                A+
              </button>
            </div>
          </div>
          <fieldset className="a11y-toggles">
            <legend>{t("a11y.features")}</legend>
            {TOGGLES.map((toggle) => (
              <label key={toggle} className="check">
                <input type="checkbox" checked={prefs[toggle]} onChange={(event) => update({ [toggle]: event.target.checked })} />
                <span>{t(`a11y.toggle.${toggle}` as MessageKey)}</span>
              </label>
            ))}
          </fieldset>
          <div className="a11y-actions">
            {"speechSynthesis" in window &&
              (speaking ? (
                <button type="button" className="btn" onClick={stopSpeaking}>
                  {t("a11y.stopReading")}
                </button>
              ) : (
                <button type="button" className="btn" onClick={() => speak(locale)}>
                  {t("a11y.read")}
                </button>
              ))}
            <button type="button" className="btn btn-ghost" onClick={reset}>
              {t("a11y.reset")}
            </button>
          </div>
          <p className="muted a11y-note">{t("a11y.note")}</p>
        </div>
      )}
    </>
  );
}
