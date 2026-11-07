#!/usr/bin/env bash
# PostgreSQL do próprio usuário para desenvolvimento, sem sudo.
#   scripts/postgres-local.sh iniciar | parar | status
# Dados em data/postgres (ignorado pelo Git, permissão 700). Escuta só em 127.0.0.1:5433, com SCRAM.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
data="$root/data/postgres"
port=5433

bin_dir="$(ls -d /usr/lib/postgresql/*/bin 2>/dev/null | sort -V | tail -1 || true)"
[[ -n "$bin_dir" && -x "$bin_dir/initdb" ]] || { echo "Não achei initdb do PostgreSQL em /usr/lib/postgresql." >&2; exit 1; }

pg_ctl_run() {
  "$bin_dir/pg_ctl" -D "$data" "$@"
}

init_cluster() {
  umask 077
  mkdir -p "$root/data"
  local admin_password pwfile
  admin_password="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | cut -c1-32)"
  pwfile="$(mktemp)"
  trap 'rm -f "$pwfile"' RETURN
  printf '%s\n' "$admin_password" > "$pwfile"
  "$bin_dir/initdb" -D "$data" -U jobdragonquest_admin --pwfile="$pwfile" -E UTF8 --locale=C.UTF-8 \
    --auth-local=scram-sha-256 --auth-host=scram-sha-256 >/dev/null
  printf '%s' "$admin_password" > "$data/.admin-password"
  chmod 600 "$data/.admin-password"
  cat >> "$data/postgresql.conf" <<EOF

# JobDragonQuest: só local.
listen_addresses = '127.0.0.1'
port = $port
unix_socket_directories = '$data'
password_encryption = 'scram-sha-256'
EOF
  echo "Cluster criado em $data."
}

case "${1:-status}" in
  iniciar)
    [[ -f "$data/PG_VERSION" ]] || init_cluster
    if pg_ctl_run status >/dev/null 2>&1; then
      echo "PostgreSQL local já está rodando em 127.0.0.1:$port."
    else
      pg_ctl_run -l "$data/servidor.log" -w start >/dev/null
      echo "PostgreSQL local iniciado em 127.0.0.1:$port."
    fi
    ;;
  parar)
    pg_ctl_run -m fast stop >/dev/null && echo "PostgreSQL local parado."
    ;;
  status)
    pg_ctl_run status
    ;;
  *)
    echo "Uso: $0 iniciar|parar|status" >&2
    exit 2
    ;;
esac
