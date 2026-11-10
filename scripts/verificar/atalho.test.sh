#!/usr/bin/env bash
# Testa o atalho (#222) sem tocar no sistema: pasta temporária com nome difícil, menu de aplicativos falso e
# curl, xdg-open, notify-send e iniciar.sh falsos. Termina com ATALHO_OK.
set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

falhar() { echo "FALHOU: $1" >&2; exit 1; }

root="$work/Meus \$Projetos \"JDQ\" 100%"
mkdir -p "$root/scripts" "$root/docs/assets" "$work/bin" "$work/xdg"
cp "$repo/scripts/abrir.sh" "$repo/scripts/instalar-atalho.sh" "$root/scripts/"
cat >"$root/scripts/iniciar.sh" <<'EOF'
#!/usr/bin/env bash
echo iniciou >>"$FAKE_LOG"
exit "${FAKE_INICIAR_STATUS:-0}"
EOF
cat >"$work/bin/curl" <<'EOF'
#!/usr/bin/env bash
exit "${FAKE_CURL_STATUS:-7}"
EOF
cat >"$work/bin/xdg-open" <<'EOF'
#!/usr/bin/env bash
echo "abriu $1" >>"$FAKE_LOG"
EOF
printf '#!/usr/bin/env bash\nexit 0\n' >"$work/bin/notify-send"
printf '#!/usr/bin/env bash\nexit 0\n' >"$work/bin/update-desktop-database"
chmod +x "$root/scripts/"*.sh "$work/bin/"*
export PATH="$work/bin:$PATH" FAKE_LOG="$work/log" XDG_DATA_HOME="$work/xdg"

# 1. Instala o .desktop com o caminho escapado como pede a especificação.
out="$(bash "$root/scripts/instalar-atalho.sh")"
[[ "$out" == *ATALHO_INSTALADO* ]] || falhar "instalação não confirmou"
desktop="$work/xdg/applications/jobdragonquest.desktop"
[[ -f "$desktop" ]] || falhar ".desktop não criado"
exec_line="$(grep '^Exec=' "$desktop")"
expected='Exec=bash "'"$work"'/Meus \$Projetos \"JDQ\" 100%%/scripts/abrir.sh"'
[[ "$exec_line" == "$expected" ]] || falhar "Exec errado: $exec_line (esperado $expected)"
grep -qx 'Terminal=false' "$desktop" || falhar "abre terminal"
[[ "$(grep -c '' "$desktop")" == 10 ]] || falhar "linhas a mais no .desktop"
if command -v desktop-file-validate >/dev/null 2>&1; then
  desktop-file-validate "$desktop" || falhar "desktop-file-validate recusou"
fi

# 2. Recusa caminho com quebra de linha (viraria outra chave no .desktop).
bad="$work/linha
Exec=rm"
mkdir -p "$bad/scripts"
cp "$repo/scripts/instalar-atalho.sh" "$bad/scripts/"
if XDG_DATA_HOME="$work/xdg2" bash "$bad/scripts/instalar-atalho.sh" >/dev/null 2>&1; then
  falhar "aceitou caminho com quebra de linha"
fi
[[ ! -e "$work/xdg2/applications/jobdragonquest.desktop" ]] || falhar "criou .desktop com caminho inválido"

# 3. Painel desligado: liga e abre o navegador no endereço local.
: >"$FAKE_LOG"
out="$(bash "$root/scripts/abrir.sh")"
sleep 0.2
[[ "$out" == *JOBDRAGONQUEST_ABERTO* ]] || falhar "abrir não confirmou"
grep -qx iniciou "$FAKE_LOG" || falhar "não chamou iniciar.sh"
grep -qx 'abriu http://127.0.0.1:5173' "$FAKE_LOG" || falhar "não abriu o navegador no endereço local"

# 4. Painel já no ar: só abre, sem chamar iniciar.sh.
: >"$FAKE_LOG"
FAKE_CURL_STATUS=0 bash "$root/scripts/abrir.sh" >/dev/null
sleep 0.2
! grep -q iniciou "$FAKE_LOG" || falhar "chamou iniciar.sh com o painel no ar"
grep -q abriu "$FAKE_LOG" || falhar "não abriu com o painel no ar"

# 5. Falha ao ligar: avisa, sai com erro e não abre o navegador.
: >"$FAKE_LOG"
if FAKE_INICIAR_STATUS=1 bash "$root/scripts/abrir.sh" >/dev/null; then falhar "falha ao ligar saiu com sucesso"; fi
! grep -q abriu "$FAKE_LOG" || falhar "abriu o navegador mesmo com falha"

# 6. Remove.
[[ "$(bash "$root/scripts/instalar-atalho.sh" remover)" == *ATALHO_REMOVIDO* ]] || falhar "remoção não confirmou"
[[ ! -e "$desktop" ]] || falhar ".desktop continua lá"

echo ATALHO_OK
