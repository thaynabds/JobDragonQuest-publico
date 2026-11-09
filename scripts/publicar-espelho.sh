#!/usr/bin/env bash
# Publica o espelho público do JobDragonQuest (github.com/thaynabds/JobDragonQuest-publico).
#
# O repositório principal é privado. O espelho recebe um commit novo com o conteúdo de main e develop, sem o histórico do
# principal, e só depois de a auditoria de vazamento passar no conteúdo de cada uma. A tag mais recente vai junto.
# Stashes, branches de trabalho e arquivos ignorados nunca vão. Não usa --force.
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

git fetch -q origin
git fetch -q --tags origin
git fetch -q "$remote" main develop

# E-mail do autor: só o endereço privado do GitHub.
allowed='^[0-9]+\+[A-Za-z0-9-]+@users\.noreply\.github\.com$'
email="$(git config user.email)"
name="$(git config user.name)"
[[ "$email" =~ $allowed ]] || { echo "O git deste clone não usa o e-mail privado do GitHub: nada foi enviado." >&2; exit 1; }
export GIT_AUTHOR_NAME="$name" GIT_AUTHOR_EMAIL="$email" GIT_COMMITTER_NAME="$name" GIT_COMMITTER_EMAIL="$email"

audit() {
  local dir
  dir="$(mktemp -d)"
  git worktree add -q --detach "$dir/copia" "$1"
  local status=0
  (cd "$dir/copia" && node scripts/verificar/vazamento.mjs >/dev/null) || status=$?
  git worktree remove --force "$dir/copia"
  rmdir "$dir"
  return "$status"
}

latest="$(git tag --list 'v*' --sort=-v:refname | head -1)"
message="chore(release): versão ${latest#v}"
pushes=()
main_commit=""
for branch in main develop; do
  source_tree="$(git rev-parse "origin/$branch^{tree}")"
  mirror_tree="$(git rev-parse "$remote/$branch^{tree}")"
  if [[ "$source_tree" == "$mirror_tree" ]]; then
    [[ "$branch" == main ]] && main_commit="$(git rev-parse "$remote/main")"
    continue
  fi
  echo "Auditoria de vazamento de $branch..."
  audit "origin/$branch" || { echo "A auditoria de $branch falhou: nada foi enviado." >&2; exit 1; }
  [[ "$mode" == "--verificar" ]] && continue
  commit="$(git commit-tree "$source_tree" -p "$remote/$branch" -m "$message")"
  pushes+=("$commit:refs/heads/$branch")
  [[ "$branch" == main ]] && main_commit="$commit"
done

if [[ "$mode" == "--verificar" ]]; then
  echo "ESPELHO_VERIFICADO_OK"
  exit 0
fi

if [[ -n "$main_commit" && -z "$(git ls-remote "$remote" "refs/tags/$latest")" ]] && [[ "$(git rev-parse "$latest^{tree}")" == "$(git rev-parse "origin/main^{tree}")" ]]; then
  body="$(git tag -l --format='%(contents)' "$latest")"
  tag="$(printf 'object %s\ntype commit\ntag %s\ntagger %s <%s> %s +0000\n\n%s\n' "$main_commit" "$latest" "$name" "$email" "$(date +%s)" "$body" | git mktag)"
  pushes+=("$tag:refs/tags/$latest")
fi

if [[ ${#pushes[@]} -gt 0 ]]; then
  git push "$remote" "${pushes[@]}"
fi
echo "Espelho público atualizado: https://github.com/thaynabds/JobDragonQuest-publico"
echo "ESPELHO_PUBLICADO_OK"
