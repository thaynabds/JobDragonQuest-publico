#!/usr/bin/env bash
# Prepara o ambiente local do JobFly e grava ../.env (permissão 600) com segredos gerados aqui.
#
#   scripts/configurar-local.sh            PostgreSQL próprio do usuário, sem sudo (padrão)
#   scripts/configurar-local.sh --sistema  usa o PostgreSQL do sistema (porta 5432, pede sudo)
#
# Modo padrão: cria um cluster em data/postgres (fora do Git), escutando só em 127.0.0.1:5433, com autenticação
# SCRAM. O superusuário do cluster (jobfly_admin) só serve para criar o papel da aplicação (jobfly), que não é
# superusuário. Nenhum segredo aparece na tela nem na lista de processos.
# Pode rodar de novo: reaproveita o cluster existente e troca a senha da aplicação.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/.env"
mode="${1:-usuario}"

command -v openssl >/dev/null || { echo "Instale o openssl." >&2; exit 1; }
command -v psql >/dev/null || { echo "Instale o cliente do PostgreSQL (psql)." >&2; exit 1; }

random_password() {
  local value
  value="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | cut -c1-32)"
  [[ "$value" =~ ^[A-Za-z0-9]{32}$ ]] || { echo "Falha ao gerar senha." >&2; exit 1; }
  printf '%s' "$value"
}

app_password="$(random_password)"
jwt_secret="$(openssl rand -base64 32)"

# SQL idempotente: cria ou atualiza o papel da aplicação e o banco. A senha entra como variável do psql.
provision_sql() {
  cat <<SQL
\set senha '$app_password'
SELECT format('CREATE ROLE jobfly LOGIN PASSWORD %L', :'senha')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jobfly') \gexec
SELECT format('ALTER ROLE jobfly WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'senha') \gexec
SELECT 'CREATE DATABASE jobfly OWNER jobfly ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'jobfly') \gexec
SQL
}

case "$mode" in
  usuario)
    port=5433
    "$root/scripts/postgres-local.sh" iniciar
    data="$root/data/postgres"
    admin_password="$(cat "$data/.admin-password")"
    provision_sql | PGPASSWORD="$admin_password" psql -v ON_ERROR_STOP=1 -q -h 127.0.0.1 -p "$port" -U jobfly_admin -d postgres
    ;;
  --sistema)
    port=5432
    provision_sql | sudo -u postgres psql -v ON_ERROR_STOP=1 -q postgres
    ;;
  *)
    echo "Uso: $0 [--sistema]" >&2
    exit 2
    ;;
esac

umask 077
cat > "$env_file" <<EOF
JOBFLY_DB_URL=jdbc:postgresql://127.0.0.1:$port/jobfly
JOBFLY_DB_USER=jobfly
JOBFLY_DB_PASSWORD=$app_password
JOBFLY_JWT_SECRET=$jwt_secret
JOBFLY_ALLOWED_ORIGINS=http://127.0.0.1:5173,http://localhost:5173
JOBFLY_SECURE_COOKIES=false
JOBFLY_PORT=8080
EOF
chmod 600 "$env_file"
echo "Banco pronto (127.0.0.1:$port/jobfly) e $env_file gravado com permissão 600."
echo "Para subir a API: scripts/iniciar-api.sh"
