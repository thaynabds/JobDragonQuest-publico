#!/usr/bin/env bash
# Abre o JobDragonQuest com um clique: sobe o que ainda não estiver rodando (scripts/iniciar.sh) e abre o painel no
# navegador. É o que o atalho do menu de aplicativos chama (scripts/instalar-atalho.sh). Saída em data/logs/abrir.log.
set -uo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
url="http://127.0.0.1:5173"
mkdir -p "$root/data/logs"
chmod 700 "$root/data" 2>/dev/null || true
log="$root/data/logs/abrir.log"

avisar() {
  echo "$1"
  if command -v notify-send >/dev/null 2>&1; then
    notify-send --app-name=JobDragonQuest "JobDragonQuest" "$1" || true
  elif command -v osascript >/dev/null 2>&1; then
    osascript -e "display notification \"${1//\"/}\" with title \"JobDragonQuest\"" || true
  fi
}

abrir_navegador() {
  if command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$url" >/dev/null 2>&1 &
  elif command -v open >/dev/null 2>&1; then
    open "$url"
  else
    echo "Abra no navegador: $url"
  fi
}

# Já no ar: só abre o navegador, sem esperar nada.
if curl -s -o /dev/null --max-time 2 "$url/"; then
  abrir_navegador
  exit 0
fi

avisar "Ligando o JobDragonQuest. A primeira vez pode levar alguns minutos."
if "$root/scripts/iniciar.sh" >>"$log" 2>&1; then
  abrir_navegador
  echo "JOBDRAGONQUEST_ABERTO"
else
  avisar "Não consegui ligar o JobDragonQuest. Veja o que aconteceu em $log"
  exit 1
fi
