import { useState, type InputHTMLAttributes } from "react";

import { useI18n } from "../i18n/I18nContext";

/** Campo de senha com o botão de mostrar ou esconder o que foi digitado. */
export function PasswordInput(props: Omit<InputHTMLAttributes<HTMLInputElement>, "type">) {
  const { t } = useI18n();
  const [visible, setVisible] = useState(false);
  return (
    <span className="password-input">
      <input {...props} type={visible ? "text" : "password"} />
      <button type="button" className="btn btn-ghost" aria-pressed={visible} onClick={() => setVisible(!visible)}>
        {visible ? t("auth.hidePassword") : t("auth.showPassword")}
      </button>
    </span>
  );
}
