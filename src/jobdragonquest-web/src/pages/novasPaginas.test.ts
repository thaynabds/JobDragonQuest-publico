import { describe, expect, it } from "vitest";

import { csvCell } from "./BoardPage";
import { buildRequest, peopleSearchUrl } from "./AssistantsPage";
import { filterAnswers } from "./ScreeningPage";

describe("novas páginas", () => {
  it("o pedido ao MCP omite campos vazios", () => {
    const text = buildRequest("entrevista", "", { jobTitle: "Dev", years: " " });
    expect(text).toContain('"jobTitle":"Dev"');
    expect(text).not.toContain("years");
  });

  it("a busca de recrutadores codifica o texto", () => {
    expect(peopleSearchUrl("A&B", "RH")).toBe("https://www.linkedin.com/search/results/people/?keywords=RH%20A%26B");
  });

  it("filtra triagem sem acento e por categoria", () => {
    const items = [{ question: "Você é fluente?", answer: "Sim", category: "LANGUAGE" }, { question: "Cidade", answer: "Recife" }];
    expect(filterAnswers(items, "voce", "")).toHaveLength(1);
    expect(filterAnswers(items, "", "OTHER")[0]?.question).toBe("Cidade");
  });

  it("CSV exportado não vira fórmula na planilha", () => {
    expect(csvCell('=HYPERLINK("x")')).toBe(`"'=HYPERLINK(""x"")"`);
  });
});
