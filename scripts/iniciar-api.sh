#!/usr/bin/env bash
# Sobe a API com as variáveis do ../.env, sem exportá-las para o shell de quem chamou.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/.env"
[[ -f "$env_file" ]] || { echo "Falta $env_file. Rode scripts/configurar-local.sh antes." >&2; exit 1; }
if [[ "$(stat -c '%a' "$env_file")" != "600" ]]; then
  echo "Aviso: $env_file deveria ter permissão 600 (chmod 600 .env)." >&2
fi

# Origens extras passadas na linha de comando (ex.: endereço do Tailscale) valem mais que as do .env.
override_origins="${JOBFLY_ALLOWED_ORIGINS:-}"
set -a
# shellcheck disable=SC1090
source "$env_file"
set +a
if [[ -n "$override_origins" ]]; then
  export JOBFLY_ALLOWED_ORIGINS="$override_origins"
fi
cd "$root/src/jobfly-api"
exec ./mvnw -q spring-boot:run
