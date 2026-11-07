#!/usr/bin/env bash
# Liga o JobFly ao Congo Grey Parrot: copia o plugin para a pasta plugins do bot de Discord e grava a configuração
# em ~/.config/jobfly/congo.env com permissão 600. O token é lido sem aparecer na tela e nunca vai para o Congo.
#
# Uso:
#   scripts/conectar-congo.sh                         (pergunta o que faltar)
#   scripts/conectar-congo.sh --congo "/caminho/congo-grey-parrot-discord" --sem-reiniciar
# Variáveis opcionais (modo sem perguntas): JOBFLY_TOKEN, JOBFLY_DISCORD_USER_ID, JOBFLY_API_URL, JOBFLY_CONGO_CONFIG.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
congo="${HOME}/Documentos/Projetos Cursor/Project Congo Grey Parrot (Psittacus erithacus)/congo-grey-parrot-discord"
config="${JOBFLY_CONGO_CONFIG:-${HOME}/.config/jobfly/congo.env}"
restart="perguntar"

while [ $# -gt 0 ]; do
  case "$1" in
    --congo) congo="${2:?informe a pasta do bot de Discord do Congo}"; shift 2 ;;
    --config) config="${2:?informe o arquivo de configuração}"; shift 2 ;;
    --sem-reiniciar) restart="nao"; shift ;;
    --reiniciar) restart="sim"; shift ;;
    -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
    *) echo "Opção desconhecida: $1" >&2; exit 1 ;;
  esac
done

if [ ! -d "$congo/plugins" ] || [ ! -d "$congo/src/congo_discord" ]; then
  echo "Não achei o bot de Discord do Congo em: $congo" >&2
  echo "Informe a pasta com --congo \"/caminho/congo-grey-parrot-discord\"." >&2
  exit 1
fi

token="${JOBFLY_TOKEN:-}"
if [ -z "$token" ]; then
  if [ ! -t 0 ]; then
    echo "Defina JOBFLY_TOKEN ou rode num terminal para digitar o token." >&2
    exit 1
  fi
  echo "Crie um token no painel do JobFly em Configurações > Integrações e tokens de acesso."
  read -r -s -p "Cole o token (não aparece na tela): " token
  echo
fi
if ! [[ "$token" =~ ^jfp_[A-Za-z0-9_-]{43}$ ]]; then
  echo "Token em formato inválido (esperado jfp_ seguido de 43 caracteres)." >&2
  exit 1
fi

owner="${JOBFLY_DISCORD_USER_ID:-}"
if [ -z "$owner" ]; then
  if [ ! -t 0 ]; then
    echo "Defina JOBFLY_DISCORD_USER_ID ou rode num terminal." >&2
    exit 1
  fi
  echo "Seu id do Discord: ative o Modo desenvolvedor em Configurações > Avançado, clique no seu nome e em Copiar ID."
  read -r -p "Seu id de usuário do Discord: " owner
fi
if ! [[ "$owner" =~ ^[0-9]{17,20}$ ]]; then
  echo "Id do Discord inválido (são 17 a 20 números)." >&2
  exit 1
fi

api="${JOBFLY_API_URL:-http://127.0.0.1:8080}"
if ! [[ "$api" =~ ^(http://(127\.0\.0\.1|localhost)(:[0-9]{1,5})?|https://[A-Za-z0-9.-]+(:[0-9]{1,5})?)/?$ ]]; then
  echo "JOBFLY_API_URL precisa ser http://127.0.0.1 ou HTTPS." >&2
  exit 1
fi

mkdir -p "$(dirname "$config")"
chmod 700 "$(dirname "$config")"
(
  umask 077
  tmp="$(mktemp "${config}.XXXXXX")"
  printf 'JOBFLY_API_URL=%s\nJOBFLY_TOKEN=%s\nJOBFLY_DISCORD_USER_ID=%s\nJOBFLY_POLL_SECONDS=60\n' "$api" "$token" "$owner" > "$tmp"
  chmod 600 "$tmp"
  mv "$tmp" "$config"
)
install -m 644 "$root/integrations/congo/jobfly.py" "$congo/plugins/jobfly.py"
echo "Plugin instalado em $congo/plugins/jobfly.py e configuração gravada em $config (permissão 600)."

if [ "$restart" = "perguntar" ] && [ -t 0 ]; then
  read -r -p "Reiniciar o bot do Congo agora (systemctl --user restart congo-discord.service)? [s/N] " answer
  [ "${answer,,}" = "s" ] && restart="sim" || restart="nao"
fi
if [ "$restart" = "sim" ]; then
  systemctl --user restart congo-discord.service
  echo "Bot reiniciado."
else
  echo "Reinicie o bot do Congo para carregar o plugin: systemctl --user restart congo-discord.service"
fi
echo "No Discord, ligue a função com: /funcoes ativar jobfly"
echo "CONGO_CONECTADO_OK"
