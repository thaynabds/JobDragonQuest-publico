// Nomes de idiomas e países no idioma do painel, pelo Intl do navegador (sem lista traduzida à mão).

/** Idiomas oferecidos na sugestão (ISO 639-1, mais Libras e ASL). Qualquer outro nome digitado também é aceito pela API. */
export const LANGUAGE_CODES = [
  "pt", "en", "es", "fr", "de", "it", "nl", "sv", "no", "da", "fi", "is", "pl", "cs", "sk", "hu", "ro", "bg", "el",
  "tr", "ru", "uk", "be", "sr", "hr", "bs", "sl", "mk", "sq", "lt", "lv", "et", "ga", "cy", "eu", "ca", "gl", "la",
  "ar", "he", "fa", "ur", "hi", "bn", "pa", "gu", "mr", "ta", "te", "kn", "ml", "si", "ne", "zh", "ja", "ko", "vi",
  "th", "id", "ms", "tl", "km", "lo", "my", "mn", "ka", "hy", "az", "kk", "uz", "sw", "am", "yo", "ig", "ha", "zu",
  "xh", "af", "so", "eo", "gn", "qu", "ht", "bzs", "ase",
] as const;

const SIGN_LANGUAGES: Record<string, Record<string, string>> = {
  bzs: { "pt-BR": "Libras (Língua Brasileira de Sinais)", en: "Brazilian Sign Language (Libras)", es: "Lengua de señas brasileña (Libras)" },
  ase: { "pt-BR": "Língua de Sinais Americana (ASL)", en: "American Sign Language (ASL)", es: "Lengua de señas americana (ASL)" },
};

/** Países (ISO 3166-1 alfa-2). */
export const COUNTRY_CODES = (
  "AD AE AF AG AI AL AM AO AR AT AU AW AZ BA BB BD BE BF BG BH BI BJ BM BN BO BR BS BT BW BY BZ CA CD CF CG CH CI CL " +
  "CM CN CO CR CU CV CY CZ DE DJ DK DM DO DZ EC EE EG ER ES ET FI FJ FM FR GA GB GD GE GH GM GN GQ GR GT GW GY HK HN HR " +
  "HT HU ID IE IL IN IQ IR IS IT JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME " +
  "MG MH MK ML MM MN MO MR MT MU MV MW MX MY MZ NA NE NG NI NL NO NP NR NZ OM PA PE PG PH PK PL PR PS PT PW PY QA RO RS " +
  "RU RW SA SB SC SD SE SG SI SK SL SM SN SO SR SS ST SV SY SZ TD TG TH TJ TL TM TN TO TR TT TV TW TZ UA UG US UY UZ VA " +
  "VC VE VN VU WS XK YE ZA ZM ZW"
).split(" ");

function displayNames(locale: string, type: "language" | "region"): Intl.DisplayNames | null {
  try {
    return new Intl.DisplayNames([locale], { type, fallback: "code" });
  } catch {
    return null;
  }
}

const capitalize = (value: string, locale: string) => (value ? value.charAt(0).toLocaleUpperCase(locale) + value.slice(1) : value);

/** Nome do idioma no idioma do painel ("en" vira "Inglês" em português). Código desconhecido volta como está. */
export function languageName(code: string, locale: string): string {
  const special = SIGN_LANGUAGES[code]?.[locale] ?? SIGN_LANGUAGES[code]?.en;
  if (special) {
    return special;
  }
  const name = displayNames(locale, "language")?.of(code);
  return name && name !== code ? capitalize(name, locale) : code;
}

export function languageOptions(locale: string): string[] {
  return [...new Set(LANGUAGE_CODES.map((code) => languageName(code, locale)))].sort((a, b) => a.localeCompare(b, locale));
}

export function countryName(code: string, locale: string): string {
  const name = displayNames(locale, "region")?.of(code);
  return name && name !== code ? name : code;
}

const plain = (value: string) => value.normalize("NFD").replace(/\p{M}+/gu, "").toLowerCase().trim();

/** "Brasil", "brazil", "BR" → "BR". Nome que não for reconhecido devolve null (o painel avisa). */
export function countryCode(text: string, locale: string): string | null {
  const value = text.trim();
  if (/^[A-Za-z]{2}$/.test(value) && COUNTRY_CODES.includes(value.toUpperCase())) {
    return value.toUpperCase();
  }
  const wanted = plain(value);
  for (const candidate of [locale, "en", "pt-BR", "es"]) {
    const found = COUNTRY_CODES.find((code) => plain(countryName(code, candidate)) === wanted);
    if (found) {
      return found;
    }
  }
  return null;
}

export function countryOptions(locale: string): string[] {
  return COUNTRY_CODES.map((code) => countryName(code, locale)).sort((a, b) => a.localeCompare(b, locale));
}
