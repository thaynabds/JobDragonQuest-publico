import { useState } from "react";

import { useI18n } from "../i18n/I18nContext";

const HOUR = 60;
const DAY = 1440;

/** Frequência livre: a cada X horas ou a cada X dias. O valor fica sempre em minutos. */
export function FrequencyInput({ value, onChange, label }: { value: number; onChange: (minutes: number) => void; label: string }) {
  const { t } = useI18n();
  const [unit, setUnit] = useState(value % DAY === 0 ? DAY : HOUR);
  const [text, setText] = useState(String(Math.max(1, Math.round(value / unit))));
  const max = (selected: number) => (selected === DAY ? 365 : 8760);
  const emit = (raw: string, selected: number) => {
    const amount = Number(raw);
    if (Number.isInteger(amount) && amount >= 1) {
      onChange(Math.min(amount, max(selected)) * selected);
    }
  };
  return (
    <span className="frequency-input">
      <span>{t("frequency.every")}</span>
      <input
        type="number"
        aria-label={`${label}: ${t("frequency.amount")}`}
        min={1}
        max={max(unit)}
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          emit(e.target.value, unit);
        }}
        onBlur={() => {
          const fixed = Math.min(Math.max(1, Math.floor(Number(text)) || 1), max(unit));
          setText(String(fixed));
          onChange(fixed * unit);
        }}
      />
      <select
        aria-label={`${label}: ${t("frequency.unit")}`}
        value={unit}
        onChange={(e) => {
          const selected = Number(e.target.value);
          setUnit(selected);
          emit(text, selected);
        }}
      >
        <option value={HOUR}>{t("frequency.hours")}</option>
        <option value={DAY}>{t("frequency.days")}</option>
      </select>
    </span>
  );
}

export function frequencyText(minutes: number, t: ReturnType<typeof useI18n>["t"]): string {
  if (minutes % DAY === 0) return t("frequency.everyDays", { count: minutes / DAY });
  if (minutes % HOUR === 0) return t("frequency.everyHours", { count: minutes / HOUR });
  return t("frequency.everyMinutes", { count: minutes });
}
