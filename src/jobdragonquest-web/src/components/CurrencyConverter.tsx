import { useMemo, useState } from "react";

import { api } from "../api/endpoints";
import { useI18n } from "../i18n/I18nContext";
import { useAsync } from "../utils/useAsync";
import { Card, ExternalLink, Field, Notice } from "./ui";

/** Valor convertido pelas cotações, que vêm com base em 1 dólar. */
export function convert(amount: number, from: string, to: string, rates: Record<string, number>): number | null {
  const source = rates[from];
  const target = rates[to];
  return source && target ? (amount / source) * target : null;
}

/** Conversor para entender um salário em outra moeda. */
export function CurrencyConverter({ initialAmount, initialCurrency }: { initialAmount?: number | null; initialCurrency?: string | null }) {
  const { t, locale, errorMessage } = useI18n();
  const data = useAsync(() => api.currencyRates().then((r) => r.data), []);
  const [amount, setAmount] = useState(String(initialAmount ?? 1000));
  const [from, setFrom] = useState(initialCurrency ?? "USD");
  const [to, setTo] = useState("BRL");
  const names = useMemo(() => new Intl.DisplayNames([locale], { type: "currency" }), [locale]);
  const codes = Object.keys(data.data?.rates ?? {});
  const result = data.data && amount !== "" ? convert(Number(amount), from, to, data.data.rates) : null;
  const option = (code: string) => (
    <option key={code} value={code}>
      {code} · {names.of(code)}
    </option>
  );
  return (
    <Card title={t("currency.title")}>
      {data.error ? <Notice tone="error">{errorMessage(data.error)}</Notice> : null}
      <div className="form-row">
        <Field label={t("currency.amount")}>
          <input type="number" min={0} step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
        </Field>
        <Field label={t("currency.from")}>
          <select value={from} onChange={(e) => setFrom(e.target.value)}>
            {codes.map(option)}
          </select>
        </Field>
        <Field label={t("currency.to")}>
          <select value={to} onChange={(e) => setTo(e.target.value)}>
            {codes.map(option)}
          </select>
        </Field>
      </div>
      {result !== null && (
        <p>
          <strong>{new Intl.NumberFormat(locale, { style: "currency", currency: to }).format(result)}</strong>
        </p>
      )}
      {data.data && (
        <p className="muted small">
          {t("currency.note", { date: data.data.updatedAt })} <ExternalLink href="https://www.exchangerate-api.com">Rates By Exchange Rate API</ExternalLink>
        </p>
      )}
    </Card>
  );
}
