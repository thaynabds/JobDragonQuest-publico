#!/usr/bin/env bash
# Liga os hooks do projeto (.githooks/pre-commit e commit-msg) neste clone. Desligar: git config --unset core.hooksPath
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
chmod +x .githooks/*
git config core.hooksPath .githooks
echo "Hooks ligados: pre-commit (lint e segredos) e commit-msg (Conventional Commits)."
