// No GitHub Actions os testes também gravam um relatório JUnit, que o CI publica e resume (scripts/resumo-testes.mjs).
import { defineConfig } from "vitest/config";

const ci = process.env.GITHUB_ACTIONS === "true";
export default defineConfig({
  test: {
    reporters: ci ? ["default", "junit"] : ["default"],
    outputFile: { junit: "test-results/junit.xml" },
  },
});
