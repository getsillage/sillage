#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

from_tag="${SILLAGE_UPGRADE_FROM_TAG:-}"
if [[ -z "$from_tag" ]]; then
  while IFS= read -r candidate; do
    if [[ "$candidate" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      from_tag="$candidate"
      break
    fi
  done < <(git tag --list 'v*' --sort=-version:refname)
fi

if [[ -z "$from_tag" ]]; then
  echo "No stable vX.Y.Z tag is available for the upgrade drill." >&2
  exit 1
fi
if ! git rev-parse --verify --quiet "${from_tag}^{commit}" >/dev/null; then
  echo "Upgrade source tag is unavailable: $from_tag" >&2
  exit 1
fi

report="$(mktemp)"
trap 'rm -f "$report"' EXIT
SILLAGE_UPGRADE_FROM_TAG="$from_tag" \
SILLAGE_UPGRADE_DRILL_REPORT="$report" \
  go test -tags=upgrade_drill -count=1 -timeout=180s ./integration/upgrade
cat "$report"
