#!/usr/bin/env bash
# Cria o atalho "JobDragonQuest" no menu de aplicativos (Linux) ou na pasta Aplicativos (macOS). Um clique sobe o que
# faltar e abre o painel no navegador, sem terminal.
#   scripts/instalar-atalho.sh            instala
#   scripts/instalar-atalho.sh remover    remove
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Caminho com quebra de linha ou caractere de controle viraria outra linha no .desktop: recusa.
if [[ "$root" == *[[:cntrl:]]* ]]; then
  echo "O caminho da pasta do JobDragonQuest tem caracteres inválidos: mova a pasta e rode de novo." >&2
  exit 1
fi

# Exec do .desktop: argumento entre aspas, com \ " ` $ escapados e % dobrado (especificação freedesktop).
desktop_quote() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//\`/\\\`}"
  value="${value//\$/\\\$}"
  value="${value//%/%%}"
  printf '"%s"' "$value"
}

if [[ "$(uname -s)" == "Darwin" ]]; then
  target="$HOME/Applications/JobDragonQuest.command"
  if [[ "${1:-}" == "remover" ]]; then
    rm -f "$target"
    echo "ATALHO_REMOVIDO"
    exit 0
  fi
  mkdir -p "$HOME/Applications"
  printf '#!/usr/bin/env bash\nexec %q\n' "$root/scripts/abrir.sh" >"$target"
  chmod 700 "$target"
  echo "Atalho criado em $target"
  echo "ATALHO_INSTALADO"
  exit 0
fi

apps="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
target="$apps/jobdragonquest.desktop"
if [[ "${1:-}" == "remover" ]]; then
  rm -f "$target"
  echo "ATALHO_REMOVIDO"
  exit 0
fi

mkdir -p "$apps"
tmp="$(mktemp "$apps/.jobdragonquest.XXXXXX")"
{
  echo "[Desktop Entry]"
  echo "Type=Application"
  echo "Version=1.0"
  echo "Name=JobDragonQuest"
  echo "Comment=Vagas, candidaturas e carreira"
  echo "Exec=bash $(desktop_quote "$root/scripts/abrir.sh")"
  echo "Icon=$root/docs/assets/logo.png"
  echo "Terminal=false"
  echo "Categories=Office;"
  echo "StartupNotify=true"
} >"$tmp"
chmod 644 "$tmp"
mv -f "$tmp" "$target"
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$apps" >/dev/null 2>&1 || true
echo "Atalho criado: procure \"JobDragonQuest\" no menu de aplicativos."
echo "ATALHO_INSTALADO"
