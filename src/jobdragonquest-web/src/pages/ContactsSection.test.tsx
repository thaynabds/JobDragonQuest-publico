import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "../i18n/I18nContext";
import { ContactsSection } from "./ContactsSection";

const api = vi.hoisted(() => ({ contacts: vi.fn(), addContact: vi.fn(), deleteContact: vi.fn() }));
vi.mock("../api/endpoints", () => ({ api }));

const saved = {
  id: "c1",
  role: "RECRUITER",
  name: "Recrutadora Fictícia",
  position: "Talent Acquisition",
  email: "recruta@empresa.test",
  linkedinUrl: "https://linkedin.com/in/recrutadora-ficticia",
  notes: "<b>não é HTML</b>",
  contactedAt: "2026-11-07T10:00:00Z",
  createdAt: "2026-11-07T10:00:00Z",
};

beforeEach(() => {
  Object.values(api).forEach((mock) => mock.mockReset());
});

describe("contatos do processo (#108)", () => {
  it("lista contatos como texto e registra um novo marcando que já conversou", async () => {
    api.contacts.mockResolvedValue({ data: [saved] });
    api.addContact.mockResolvedValue({ data: { ...saved, id: "c2" } });
    const onChanged = vi.fn();
    const user = userEvent.setup({ delay: null });
    render(
      <I18nProvider initial="pt-BR">
        <ContactsSection applicationId="a1" onChanged={onChanged} />
      </I18nProvider>,
    );

    expect(await screen.findByText("Recrutadora Fictícia")).toBeTruthy();
    // Notas aparecem como texto, nunca como HTML.
    expect(screen.getByText("<b>não é HTML</b>")).toBeTruthy();
    expect(screen.getByRole("link", { name: /LinkedIn/ }).getAttribute("rel")).toContain("noopener");

    await user.type(screen.getByRole("textbox", { name: "Nome" }), "Gestor Fictício");
    await user.selectOptions(screen.getByRole("combobox", { name: "Papel" }), "HIRING_MANAGER");
    await user.type(screen.getByRole("textbox", { name: "E-mail" }), "gestor@empresa.test");
    await user.click(screen.getByRole("checkbox", { name: /Já conversei/ }));
    await user.click(screen.getByRole("button", { name: "Adicionar contato" }));

    await waitFor(() => expect(api.addContact).toHaveBeenCalledTimes(1));
    expect(api.addContact.mock.calls[0]).toEqual([
      "a1",
      { role: "HIRING_MANAGER", name: "Gestor Fictício", position: null, email: "gestor@empresa.test", linkedinUrl: null, notes: null, contacted: true },
    ]);
    await waitFor(() => expect(onChanged).toHaveBeenCalled());
    expect(api.contacts).toHaveBeenCalledTimes(2);
  });

  it("remove um contato depois de confirmar", async () => {
    api.contacts.mockResolvedValue({ data: [saved] });
    api.deleteContact.mockResolvedValue({ data: undefined });
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const user = userEvent.setup({ delay: null });
    render(
      <I18nProvider initial="pt-BR">
        <ContactsSection applicationId="a1" onChanged={() => undefined} />
      </I18nProvider>,
    );
    await user.click(await screen.findByRole("button", { name: "Remover Recrutadora Fictícia" }));
    await waitFor(() => expect(api.deleteContact).toHaveBeenCalledWith("a1", "c1"));
  });
});
