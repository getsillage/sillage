#!/usr/bin/env bash
# Ensure the Web production build produced embeddable assets and that they stay untracked.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f server/router/frontend/dist/index.html ]]; then
  echo "Missing server/router/frontend/dist/index.html — run the Web production build first." >&2
  exit 1
fi

tracked="$(git ls-files -- server/router/frontend/dist || true)"
if [[ -n "$tracked" ]]; then
  printf 'Generated Web assets must not be tracked:\n%s\n' "$tracked" >&2
  exit 1
fi

git check-ignore -q server/router/frontend/dist/index.html

echo "Embedded Web asset policy checks passed."
