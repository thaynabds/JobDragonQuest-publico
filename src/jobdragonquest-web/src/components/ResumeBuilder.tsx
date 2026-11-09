import { useMemo, useState } from "react";

import type { ProfileInput } from "../api/types";
import { useI18n } from "../i18n/I18nContext";
import type { MessageKey } from "../i18n/messages";
import { downloadDocx } from "../utils/docx";
import { resumeGroups, setItemVisible, visibleBlocks } from "../utils/resumeModel";
import { Button } from "./ui";

/** Painel lateral: mostra o currículo enquanto o perfil é editado, com a escolha de cada dado e o download em Word. */
export function ResumeBuilder({ form, onPatch }: { form: ProfileInput; onPatch: (change: Partial<ProfileInput>) => void }) {
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const name = form.options?.socialName && (form.options.resumeVisible ?? []).includes("SOCIAL_NAME") ? form.options.socialName : form.name;
  const groups = useMemo(() => resumeGroups(form, (key) => t(key as MessageKey)), [form, t]);
  return (
    <>
      <Button variant="primary" className="resume-builder-open" aria-expanded={open} onClick={() => setOpen(!open)}>
        {t("resume.builder.title")}
      </Button>
      {open && (
        <aside className="resume-builder" aria-label={t("resume.builder.title")}>
          <header className="card-header">
            <h2>{t("resume.builder.title")}</h2>
            <div className="card-actions">
              <Button variant="primary" onClick={() => downloadDocx(visibleBlocks(name, groups), name)}>{t("resume.builder.word")}</Button>
              <Button variant="ghost" onClick={() => setOpen(false)}>{t("resume.builder.close")}</Button>
            </div>
          </header>
          <p className="muted small">{t("resume.builder.hint")}</p>
          <h3 className="resume-builder-name">{name}</h3>
          {groups.map((group) => (
            <section key={group.title}>
              <h4>{group.title}</h4>
              {group.items.map((item) => (
                <label key={item.key} className={`resume-builder-item${item.visible ? "" : " is-hidden"}`}>
                  <input type="checkbox" checked={item.visible} aria-label={`${t("resume.builder.show")}: ${item.text}`} onChange={(e) => onPatch(setItemVisible(form, item.key, e.target.checked))} />
                  <span>
                    {item.text}
                    {item.detail && <small className="prewrap">{item.detail}</small>}
                  </span>
                </label>
              ))}
            </section>
          ))}
        </aside>
      )}
    </>
  );
}
