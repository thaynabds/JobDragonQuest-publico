#!/usr/bin/env bash
# Prepara o ambiente local do JobFly:
#   1. cria o papel e o banco "jobfly" no PostgreSQL local (pede sudo para o usuário postgres);
#   2. gera senha do banco e segredo JWT aleatórios;
#   3. grava ../.env com permissão 600 (só a dona lê).
# Não imprime nenhum segredo na tela. Pode rodar de novo: o papel existente só tem a senha trocada.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/.env"

command -v openssl >/dev/null || { echo "Instale o openssl." >&2; exit 1; }
command -v psql >/dev/null || { echo "Instale o cliente do PostgreSQL (psql)." >&2; exit 1; }

db_password="$(openssl rand -base64 30 | tr -d '/+=' | cut -c1-32)"
jwt_secret="$(openssl rand -base64 32)"

[[ "$db_password" =~ ^[A-Za-z0-9]{32}$ ]] || { echo "Falha ao gerar a senha." >&2; exit 1; }

# A senha vai pelo stdin (não aparece no ps) e entra no SQL como variável do psql (:'senha'), com format(%L).
sudo -u postgres psql -v ON_ERROR_STOP=1 postgres <<SQL
\set senha '$db_password'
SELECT format('CREATE ROLE jobfly LOGIN PASSWORD %L', :'senha')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jobfly') \gexec
SELECT format('ALTER ROLE jobfly WITH LOGIN PASSWORD %L', :'senha') \gexec
SELECT 'CREATE DATABASE jobfly OWNER jobfly'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'jobfly') \gexec
SQL

umask 077
cat > "$env_file" <<EOF
JOBFLY_DB_URL=jdbc:postgresql://127.0.0.1:5432/jobfly
JOBFLY_DB_USER=jobfly
JOBFLY_DB_PASSWORD=$db_password
JOBFLY_JWT_SECRET=$jwt_secret
JOBFLY_ALLOWED_ORIGINS=http://localhost:5173
JOBFLY_SECURE_COOKIES=false
JOBFLY_PORT=8080
EOF
chmod 600 "$env_file"
echo "Banco pronto e $env_file gravado (permissão 600)."
echo "Para subir a API: scripts/iniciar-api.sh"
