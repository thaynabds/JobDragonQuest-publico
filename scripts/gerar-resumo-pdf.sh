#!/usr/bin/env bash
# Gera docs/resumo-jobdragonquest.pdf a partir de docs/resumo/resumo-jobdragonquest.html com o Chrome/Chromium sem interface.
# Uso: scripts/gerar-resumo-pdf.sh <versão>   (ex.: 0.3.0)
set -euo pipefail
version="${1:?informe a versão, ex.: 0.3.0}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "versão inválida: $version" >&2; exit 1; }
root="$(cd "$(dirname "$0")/.." && pwd)"
browser="$(command -v google-chrome || command -v chromium || command -v chromium-browser || true)"
[ -n "$browser" ] || { echo "Chrome ou Chromium não encontrado." >&2; exit 1; }
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/resumo" "$work/assets"
cp "$root/docs/assets/logo.png" "$work/assets/logo.png"
sed "s/{{VERSAO}}/$version/" "$root/docs/resumo/resumo-jobdragonquest.html" > "$work/resumo/resumo-jobdragonquest.html"
"$browser" --headless=new --disable-gpu --no-sandbox --user-data-dir="$work/perfil" --no-pdf-header-footer \
  --print-to-pdf="$work/resumo.pdf" "file://$work/resumo/resumo-jobdragonquest.html" >/dev/null 2>&1
mv "$work/resumo.pdf" "$root/docs/resumo-jobdragonquest.pdf"
echo "PDF gerado: docs/resumo-jobdragonquest.pdf (versão $version)"
