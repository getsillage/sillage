#!/usr/bin/env bash
set -euo pipefail

web_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../web" && pwd)"
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
image_ref="${SUPPLY_CHAIN_IMAGE:-sillage:dev}"

pnpm --dir "$web_dir" audit --audit-level=high
node "$repo_root/scripts/generate-third-party-notices.mjs" --check

scan_dir="$(mktemp -d)"
trap 'rm -rf "$scan_dir"' EXIT
"$repo_root/scripts/scan-container.sh" "docker:$image_ref" "$scan_dir" sillage

docker run --rm --entrypoint /bin/sh "$image_ref" -eu -c \
  'test -s /usr/share/licenses/sillage/LICENSE &&
   test -s /usr/share/licenses/sillage/NOTICE &&
   test -s /usr/share/licenses/sillage/THIRD_PARTY_NOTICES.md &&
   test -d /usr/share/licenses/sillage/third-party'

echo "Supply-chain checks passed for $image_ref"
