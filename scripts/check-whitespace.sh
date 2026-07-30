#!/usr/bin/env bash
# Fail on whitespace errors in the commit range or the current tree.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

BASE_SHA="${BASE_SHA:-${BASE_REF:-}}"
HEAD_SHA="${HEAD_SHA:-HEAD}"

if [[ -n "$BASE_SHA" ]] && git cat-file -e "${BASE_SHA}^{commit}" 2>/dev/null; then
  git diff --check "$BASE_SHA" "$HEAD_SHA"
else
  git diff --check
  git diff --cached --check
fi

echo "Whitespace checks passed."
