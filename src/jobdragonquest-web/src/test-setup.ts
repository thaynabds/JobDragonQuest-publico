import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

afterEach(() => {
  cleanup();
});

// Telas grandes demoram mais com a máquina carregada: evita falha por tempo em buscas assíncronas.
import { configure } from "@testing-library/react";
configure({ asyncUtilTimeout: 5000 });
