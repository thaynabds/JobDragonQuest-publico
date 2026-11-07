// Lint do painel: regras recomendadas de JavaScript e TypeScript, regras dos hooks do React e proibições de segurança
// (eval, innerHTML e afins), além do que o TypeScript estrito já cobre.
import js from "@eslint/js";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";
import tseslint from "typescript-eslint";

export default tseslint.config(
  { ignores: ["dist/", "node_modules/", "test-results/", "coverage/"] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["**/*.{ts,tsx}"],
    languageOptions: { ecmaVersion: 2022, globals: { ...globals.browser } },
    plugins: { "react-hooks": reactHooks },
    rules: {
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "warn",
      "no-eval": "error",
      "no-implied-eval": "error",
      "no-new-func": "error",
      "no-restricted-syntax": [
        "error",
        { selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']", message: "Não injete HTML: mostre o conteúdo como texto." },
        { selector: "AssignmentExpression[left.property.name=/^(innerHTML|outerHTML)$/]", message: "Não injete HTML: use textContent." },
      ],
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_", varsIgnorePattern: "^_" }],
    },
  },
  {
    files: ["**/*.test.{ts,tsx}", "src/test-setup.ts", "vite.config.ts"],
    languageOptions: { globals: { ...globals.node } },
  },
);
