#!/usr/bin/env bash
# Instala e sobe o JobFly num comando, no próprio computador (sem deploy e sem nada pago).
#
#   scripts/instalar.sh               confere pré-requisitos, configura o banco local, instala, compila e sobe tudo
#   scripts/instalar.sh --verificar   só confere os pré-requisitos
#   scripts/instalar.sh --sem-iniciar instala e compila, mas não sobe
#
# Pode rodar de novo: reaproveita o banco e o .env existentes. Nunca usa sudo por conta própria; se faltar algo,
# mostra o comando de instalação para você rodar.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mode="${1:-instalar}"
case "$mode" in instalar|--verificar|--sem-iniciar) ;; *) echo "Opção desconhecida: $mode" >&2; exit 1 ;; esac

missing=()
hint=()

version_major() { sed -E 's/[^0-9]*([0-9]+).*/\1/' <<<"$1"; }

if command -v java >/dev/null; then
  java_version="$(java -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')"
  [[ "$(version_major "$java_version")" -ge 21 ]] || { missing+=("Java 21 ou mais novo (achei $java_version)"); hint+=("sudo apt install openjdk-21-jdk"); }
else
  missing+=("Java 21"); hint+=("sudo apt install openjdk-21-jdk")
fi

if command -v node >/dev/null; then
  node_version="$(node -v | tr -d v)"
  node_major="${node_version%%.*}"
  node_minor="$(cut -d. -f2 <<<"$node_version")"
  if [[ "$node_major" -lt 20 || ( "$node_major" -eq 20 && "$node_minor" -lt 19 ) ]]; then
    missing+=("Node.js 20.19 ou mais novo (achei $node_version)"); hint+=("instale pelo nvm: https://github.com/nvm-sh/nvm (nvm install 20)")
  fi
else
  missing+=("Node.js 20.19+"); hint+=("instale pelo nvm: https://github.com/nvm-sh/nvm (nvm install 20)")
fi
command -v npm >/dev/null || { missing+=("npm"); hint+=("vem junto com o Node.js"); }
command -v openssl >/dev/null || { missing+=("openssl"); hint+=("sudo apt install openssl"); }
command -v psql >/dev/null || { missing+=("PostgreSQL (cliente psql)"); hint+=("sudo apt install postgresql"); }
ls /usr/lib/postgresql/*/bin/initdb >/dev/null 2>&1 || { missing+=("PostgreSQL (initdb)"); hint+=("sudo apt install postgresql"); }

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "Faltam pré-requisitos:"
  for i in "${!missing[@]}"; do
    echo "  - ${missing[$i]}: ${hint[$i]}"
  done
  exit 1
fi
echo "Pré-requisitos ok: Java $java_version, Node $node_version, PostgreSQL e openssl."
[[ "$mode" == "--verificar" ]] && { echo "PRE_REQUISITOS_OK"; exit 0; }

cd "$root"
if [[ ! -f .env ]]; then
  echo "Configurando o banco local (PostgreSQL só seu, em 127.0.0.1:5433)..."
  scripts/configurar-local.sh
else
  echo ".env encontrado: reaproveitando a configuração."
fi

echo "Instalando o painel..."
(cd src/jobfly-web && npm ci --no-audit --no-fund --loglevel=error)
echo "Instalando e compilando o servidor MCP..."
(cd src/jobfly-mcp && npm ci --no-audit --no-fund --loglevel=error && npm run build --silent)
echo "Compilando a API..."
(cd src/jobfly-api && ./mvnw -q -B -DskipTests package)

if [[ "$mode" == "--sem-iniciar" ]]; then
  echo "Instalado. Para subir: scripts/iniciar.sh"
else
  scripts/iniciar.sh
fi

cat <<'EOF'

Próximos passos:
  1. Abra http://127.0.0.1:5173 e crie sua conta.
  2. Em Configurações > Integrações e tokens de acesso, crie um token.
  3. Ligue a sua IA ao JobFly pelo MCP (o token é digitado sem aparecer na tela):
       node scripts/conectar-mcp.mjs --cliente claude-desktop   (ou claude-code, cursor, json)
  4. Opcional: avisos pelo Discord com o Congo Grey Parrot: scripts/conectar-congo.sh
EOF
echo "INSTALACAO_CONCLUIDA"
