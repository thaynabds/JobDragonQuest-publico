#!/usr/bin/env bash
# Publica o espelho público do JobDragonQuest (github.com/thaynabds/JobDragonQuest-publico).
#
# O repositório principal é privado. O espelho recebe só main, develop e as tags v*, e só depois de passar por:
#   1. árvore de trabalho limpa;
#   2. auditoria de vazamento (scripts/verificar/vazamento.mjs);
#   3. todos os commits e tags enviados com e-mail privado do GitHub (nunca um e-mail pessoal);
#   4. nenhum commit com notas internas ou com a pasta privado/.
# Stashes, branches de trabalho e arquivos ignorados nunca vão. Não usa --force: se o espelho divergir, para.
#
# Uso: scripts/publicar-espelho.sh [--verificar]   (--verificar faz as checagens e não envia nada)
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
mode="${1:-publicar}"
case "$mode" in publicar|--verificar) ;; *) echo "Opção desconhecida: $mode" >&2; exit 1 ;; esac

remote="publico"
url="git@github.com:thaynabds/JobDragonQuest-publico.git"
git remote get-url "$remote" >/dev/null 2>&1 || git remote add "$remote" "$url"

[[ -z "$(git status --porcelain)" ]] || { echo "Há mudanças não commitadas. Commite ou descarte antes de publicar." >&2; exit 1; }

echo "Auditoria de vazamento..."
node scripts/verificar/vazamento.mjs >/dev/null || { echo "A auditoria de vazamento falhou: nada foi enviado. Rode node scripts/verificar/vazamento.mjs para ver." >&2; exit 1; }

mapfile -t tags < <(git tag --list 'v*')
refs=(main develop "${tags[@]}")

# E-mails permitidos: só os endereços privados do GitHub.
allowed='^([0-9]+\+[A-Za-z0-9-]+@users\.noreply\.github\.com|noreply@github\.com)$'
bad="$(git log --format='%ae%n%ce' "${refs[@]}" | sort -u | grep -Ev "$allowed" || true)"
[[ -z "$bad" ]] || { echo "Há commits com e-mail que não é privado do GitHub: nada foi enviado." >&2; exit 1; }
for tag in "${tags[@]}"; do
  tagger="$(git for-each-ref "refs/tags/$tag" --format='%(taggeremail)' | tr -d '<>')"
  [[ -z "$tagger" || "$tagger" =~ $allowed ]] || { echo "A tag $tag tem e-mail que não é privado do GitHub: nada foi enviado." >&2; exit 1; }
done
notes="$(git log --format=%H "${refs[@]}" -- ':(glob)**/_notas_trabalho.md' ':(glob)privado/**' | wc -l)"
[[ "$notes" -eq 0 ]] || { echo "Algum commit traz as notas internas da auditoria: nada foi enviado." >&2; exit 1; }
echo "Checagens ok: ${#tags[@]} tags, e-mails privados, sem notas internas."

if [[ "$mode" == "--verificar" ]]; then
  echo "ESPELHO_VERIFICADO_OK"
  exit 0
fi

git push "$remote" main develop
git push "$remote" "${tags[@]/#/refs/tags/}"
echo "Espelho público atualizado: https://github.com/thaynabds/JobDragonQuest-publico"
echo "ESPELHO_PUBLICADO_OK"
