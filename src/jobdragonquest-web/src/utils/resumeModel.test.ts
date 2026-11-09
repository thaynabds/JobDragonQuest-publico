import { describe, expect, it } from "vitest";

import type { ProfileInput } from "../api/types";
import { buildDocx, documentXml, safeFileName, xmlText } from "./docx";
import { resumeGroups, setItemVisible, visibleBlocks } from "./resumeModel";

const label = (key: string) => key;
const form = {
  name: "Pessoa",
  headline: "Dev Java",
  skills: [{ name: "Java", level: 4, years: 1, months: 0, days: 0 }, { name: "Cobol", level: 1, years: 0, months: 0, days: 0 }],
  languages: [],
  entries: [],
  options: { phone: "999", birthDate: "2000-01-02", diversityOther: ["Texto livre"], diversity: [] },
} as unknown as ProfileInput;

const texts = (f: ProfileInput) => visibleBlocks("Pessoa", resumeGroups(f, label)).flatMap((b) => b.lines.map((l) => l.text));

describe("modelo do currículo", () => {
  it("dado sensível só aparece quando a pessoa liga", () => {
    expect(texts(form).join()).not.toContain("2000-01-02");
    expect(texts(form).join()).not.toContain("999");
    expect(texts(form).join()).not.toContain("Texto livre");
    const on = { ...form, ...setItemVisible(form, "section:BIRTH_DATE", true) };
    expect(texts(on).join()).toContain("2000-01-02");
  });

  it("esconde e mostra um item por vez", () => {
    const off = { ...form, ...setItemVisible(form, "skill:1", false) };
    expect(texts(off)).toContain("Java");
    expect(texts(off)).not.toContain("Cobol");
    expect(texts({ ...off, ...setItemVisible(off, "skill:1", true) })).toContain("Cobol");
  });

  it("tirar o telefone não liga o e-mail nem o resto do contato", () => {
    const on = { ...form, ...setItemVisible(form, "contact:PHONE", true) };
    expect(texts(on)).toContain("999");
    expect(texts({ ...on, ...setItemVisible(on, "contact:PHONE", false) })).not.toContain("999");
  });
});

describe("arquivo Word", () => {
  it("escapa marcação e caracteres de controle", () => {
    expect(xmlText('<a>&"\u0001')).toBe('&lt;a&gt;&amp;"');
    expect(documentXml([{ heading: "X", lines: [{ text: "</w:t><evil/>" }] }])).not.toContain("<evil/>");
  });

  it("gera um pacote ZIP com o documento e só o que foi marcado", () => {
    const off = { ...form, ...setItemVisible(form, "skill:1", false) };
    const bytes = buildDocx(visibleBlocks("Pessoa", resumeGroups(off, label)));
    const text = new TextDecoder().decode(bytes);
    expect(bytes[0]).toBe(0x50);
    expect(bytes[1]).toBe(0x4b);
    expect(text).toContain("word/document.xml");
    expect(text).toContain("Java");
    expect(text).not.toContain("Cobol");
  });

  it("nome do arquivo não escapa da pasta", () => {
    expect(safeFileName("../../etc/passwd")).toBe("etc_passwd.docx");
    expect(safeFileName("///")).toBe("curriculo.docx");
  });
});
