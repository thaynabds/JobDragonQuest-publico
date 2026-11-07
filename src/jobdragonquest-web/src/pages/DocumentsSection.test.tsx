import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { DocumentsSection } from "./DocumentsSection";

const api = vi.hoisted(() => ({
  profileDocuments: vi.fn(),
  saveProfileDocument: vi.fn(),
  readDocument: vi.fn(),
  readLinkDocument: vi.fn(),
  readStoredDocument: vi.fn(),
  appendEntry: vi.fn(),
  deleteDocument: vi.fn(),
  downloadDocument: vi.fn(),
}));
vi.mock("../api/endpoints", () => ({ api }));

const proposal = {
  type: "PDF",
  text: "Certificamos que concluiu o curso de Java",
  ocrUnavailable: false,
  proposal: {
    kind: "COURSE",
    title: "Java e Spring Boot",
    organization: "Udemy",
    date: "2026-10-15",
    hours: 40,
    credentialId: "UC-1234",
    url: null,
    skills: ["java"],
  },
};

beforeEach(() => {
  Object.values(api).forEach((mock) => mock.mockReset());
  api.profileDocuments.mockResolvedValue({ data: [] });
});

const renderSection = (onAdded = vi.fn()) =>
  render(
    <I18nProvider initial="pt-BR">
      <DocumentsSection profileId="p1" onAdded={onAdded} />
    </I18nProvider>,
  );

describe("documentos e atualização do currículo (#124)", () => {
  it("lê um certificado, deixa corrigir a proposta e só grava depois de confirmar", async () => {
    api.readDocument.mockResolvedValue({ data: proposal });
    api.appendEntry.mockResolvedValue({ data: { id: "p1", entries: [] } });
    const onAdded = vi.fn();
    const user = userEvent.setup({ delay: null });
    renderSection(onAdded);

    const input = (await screen.findByText("Ler arquivo (PDF, Word, texto ou foto)")).querySelector("input")!;
    await user.upload(input, new File(["%PDF-1.7"], "certificado.pdf", { type: "application/pdf" }));
    expect(await screen.findByDisplayValue("Java e Spring Boot")).toBeTruthy();
    expect(api.appendEntry).not.toHaveBeenCalled();

    const title = screen.getByRole("textbox", { name: "Título" });
    await user.clear(title);
    await user.type(title, "Java com Spring Boot");
    await user.click(screen.getByRole("button", { name: "Adicionar ao perfil" }));
    await waitFor(() => expect(api.appendEntry).toHaveBeenCalledTimes(1));
    expect(api.appendEntry.mock.calls[0]).toEqual([
      "p1",
      {
        kind: "COURSE",
        title: "Java com Spring Boot",
        organization: "Udemy",
        endDate: "2026-10-15",
        hours: 40,
        credentialId: "UC-1234",
        url: null,
        skills: "java",
        status: "COMPLETED",
      },
    ]);
    expect(onAdded).toHaveBeenCalled();
    expect(await screen.findByText(/Item adicionado ao perfil/)).toBeTruthy();
  });

  it("lê link, avisa foto sem texto e recusa arquivo grande sem enviar", async () => {
    api.readLinkDocument.mockResolvedValue({ data: { ...proposal, type: "LINK" } });
    const user = userEvent.setup({ delay: null });
    renderSection();
    await user.type(await screen.findByRole("textbox", { name: "Link do certificado ou curso" }), "https://ude.my/UC-1234");
    await user.click(screen.getByRole("button", { name: "Ler link" }));
    await waitFor(() => expect(api.readLinkDocument).toHaveBeenCalledWith("https://ude.my/UC-1234"));

    api.readDocument.mockResolvedValue({ data: { ...proposal, type: "PNG", text: "", ocrUnavailable: true } });
    const input = screen.getByText("Ler arquivo (PDF, Word, texto ou foto)").querySelector("input")!;
    await user.upload(input, new File(["x"], "foto.png", { type: "image/png" }));
    expect(await screen.findByText(/instale o Tesseract/)).toBeTruthy();

    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], "grande.pdf", { type: "application/pdf" });
    api.readDocument.mockClear();
    await user.upload(input, big);
    expect(await screen.findByText("O arquivo passa de 5 MB.")).toBeTruthy();
    expect(api.readDocument).not.toHaveBeenCalled();
  });

  it("guarda currículo com versão e lista os guardados", async () => {
    api.profileDocuments.mockResolvedValue({ data: [] });
    api.saveProfileDocument.mockResolvedValue({ data: {} });
    const user = userEvent.setup({ delay: null });
    renderSection();
    const input = (await screen.findByText("Guardar documento")).querySelector("input")!;
    const file = new File(["%PDF"], "cv.pdf", { type: "application/pdf" });
    api.profileDocuments.mockResolvedValue({
      data: [
        {
          id: "d1",
          profileId: "p1",
          applicationId: null,
          kind: "RESUME",
          name: "cv.pdf",
          contentType: "application/pdf",
          sizeBytes: 2048,
          version: 2,
          createdAt: "2026-11-07T10:00:00Z",
        },
      ],
    });
    await user.upload(input, file);
    await waitFor(() => expect(api.saveProfileDocument).toHaveBeenCalledWith("p1", "RESUME", file));
    expect(await screen.findByText(/Currículo · v2/)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Baixar cv.pdf" })).toBeTruthy();
  });
});
