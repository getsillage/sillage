#!/usr/bin/env bash
set -euo pipefail

web_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../apps/web" && pwd)"
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
image_ref="${SUPPLY_CHAIN_IMAGE:-sillage:dev}"

pnpm --dir "$web_dir" audit --audit-level=high
node "$repo_root/scripts/generate-third-party-notices.mjs" --check

scan_dir="$(mktemp -d)"
container=""
cleanup() {
  if [[ -n "$container" ]]; then
    docker rm -fv "$container" >/dev/null || true
  fi
  rm -rf "$scan_dir"
}
trap cleanup EXIT
"$repo_root/scripts/scan-container.sh" "docker:$image_ref" "$scan_dir" sillage

container="$(docker create --network none "$image_ref")"
docker cp "$container:/usr/share/licenses/sillage" "$scan_dir/licenses"
test -s "$scan_dir/licenses/LICENSE"
test -s "$scan_dir/licenses/NOTICE"
test -s "$scan_dir/licenses/THIRD_PARTY_NOTICES.md"
test -d "$scan_dir/licenses/third-party"

# The image owns the probe so both docker run and Compose check the same path.
docker start "$container" >/dev/null
health=""
for (( attempt = 0; attempt < 30; attempt++ )); do
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$container")"
  if [[ "$health" == healthy || "$health" == unhealthy || -z "$health" ]] ||
     [[ "$(docker inspect --format '{{.State.Running}}' "$container")" != true ]]; then
    break
  fi
  sleep 1
done
if [[ "$health" != healthy ]]; then
  docker logs "$container" >&2
  echo "Final image failed its readiness check: ${health:-no healthcheck}" >&2
  exit 1
fi

echo "Supply-chain checks passed for $image_ref"
