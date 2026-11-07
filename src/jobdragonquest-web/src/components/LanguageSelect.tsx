import { useI18n } from "../i18n/I18nContext";
import { isLocale, LOCALE_NAMES, LOCALES, type Locale } from "../i18n/messages";

/** Seletor de idioma com todos os idiomas registrados, cada um escrito nele mesmo. */
export function LanguageSelect({ className }: { className?: string }) {
  const { t, locale, setLocale } = useI18n();
  return (
    <label className={className ?? "lang"}>
      <span>{t("lang.label")}</span>
      <select
        value={locale}
        onChange={(event) => {
          if (isLocale(event.target.value)) {
            setLocale(event.target.value);
          }
        }}
      >
        {(Object.keys(LOCALES) as Locale[]).map((code) => (
          <option key={code} value={code} lang={code}>
            {LOCALE_NAMES[code]}
          </option>
        ))}
      </select>
    </label>
  );
}
