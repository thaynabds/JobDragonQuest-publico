#!/usr/bin/env bash
# Sobe o JobFly inteiro com um comando: PostgreSQL local, API e painel web (build de produção).
#   scripts/iniciar.sh         sobe tudo e mostra o endereço do painel
#   scripts/iniciar.sh parar   para o painel, a API e o banco
# Logs em data/logs (fora do Git). Tudo escuta só em 127.0.0.1.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
logs="$root/data/logs"
mkdir -p "$logs"
chmod 700 "$root/data"

# Cada serviço roda numa sessão própria (setsid): o pid gravado é o líder do grupo, e parar encerra o grupo inteiro
# (npm e o vite filho; o script da API e o java filho), sem deixar processo órfão escutando na porta.
stop_pid() {
  local file="$logs/$1.pid"
  if [[ -f "$file" ]]; then
    local pid
    pid="$(cat "$file")"
    if kill -0 "$pid" 2>/dev/null; then
      kill -- "-$pid" 2>/dev/null || kill "$pid"
      for _ in $(seq 1 30); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
      echo "$1 parado."
    fi
  fi
  rm -f "$file"
}

if [[ "${1:-}" == "parar" ]]; then
  stop_pid web
  stop_pid api
  "$root/scripts/postgres-local.sh" parar || true
  exit 0
fi

[[ -f "$root/.env" ]] || "$root/scripts/configurar-local.sh"
"$root/scripts/postgres-local.sh" iniciar

wait_for() {
  local url="$1" name="$2" seconds="$3"
  for _ in $(seq 1 "$seconds"); do
    if curl -s -o /dev/null --max-time 2 "$url"; then
      echo "$name no ar."
      return 0
    fi
    sleep 1
  done
  echo "$name não respondeu em ${seconds}s. Veja $logs/$name.log" >&2
  return 1
}

if ! curl -s -o /dev/null --max-time 2 http://127.0.0.1:8080/actuator/health; then
  setsid nohup "$root/scripts/iniciar-api.sh" > "$logs/api.log" 2>&1 < /dev/null &
  echo $! > "$logs/api.pid"
fi
wait_for http://127.0.0.1:8080/actuator/health api 600

cd "$root/src/jobfly-web"
[[ -d node_modules ]] || npm ci --no-audit --no-fund
if ! curl -s -o /dev/null --max-time 2 http://127.0.0.1:5173/; then
  npm run build > "$logs/web-build.log" 2>&1
  setsid nohup npm run preview > "$logs/web.log" 2>&1 < /dev/null &
  echo $! > "$logs/web.pid"
fi
wait_for http://127.0.0.1:5173/ web 120

echo
echo "JobFly pronto: http://127.0.0.1:5173"
echo "Para parar: scripts/iniciar.sh parar"
