#!/usr/bin/env bash
# Buf lint, optional breaking check, generate, and committed artifact drift.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v buf >/dev/null 2>&1; then
  echo "buf CLI is required (see CONTRIBUTING.md)." >&2
  exit 1
fi

buf lint

BASE_SHA="${BASE_SHA:-${BASE_REF:-}}"
if [[ -n "$BASE_SHA" ]] && git cat-file -e "${BASE_SHA}^{commit}" 2>/dev/null; then
  buf breaking --against ".git#ref=${BASE_SHA}"
elif [[ -n "${GITHUB_BASE_REF:-}" ]]; then
  buf breaking --against ".git#branch=${GITHUB_BASE_REF}"
else
  echo "check-proto: no BASE_SHA; skipping buf breaking (lint + generate only)."
fi

buf generate

changes="$({
  git diff --name-only -- contracts/proto/gen
  git ls-files --others --exclude-standard -- contracts/proto/gen
} | sort -u)"
if [[ -n "$changes" ]]; then
  printf '%s\n' "$changes"
  git diff -- contracts/proto/gen || true
  echo "contracts/proto/gen is out of date; run buf generate and commit the result." >&2
  exit 1
fi

echo "Proto checks passed."
