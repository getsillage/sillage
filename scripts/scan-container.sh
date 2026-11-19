#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: scripts/scan-container.sh <docker-source> <output-dir> <file-prefix>" >&2
  exit 2
fi

source_ref="$1"
output_dir="$2"
file_prefix="$3"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"

# Pinned multi-architecture scanner images. Update both the digest and version
# comment together, then rerun the supply-chain gate.
syft_image="${SYFT_IMAGE:-anchore/syft@sha256:1288ea4c8b38767b4e620c1e312c8cb26b6e887a99b4f07ab6cd19fc6f225026}" # v1.50.0
grype_image="${GRYPE_IMAGE:-anchore/grype@sha256:1e71065c0a4cff3e6bd3b8add525ffac4343eb4971694eb90a31cf6d4d3e85db}" # v0.116.1
fail_on="${GRYPE_FAIL_ON:-high}"
db_archive="${SILLAGE_GRYPE_DB_ARCHIVE:-}"
db_checksum="${SILLAGE_GRYPE_DB_SHA256:-}"

if [[ -n "$db_archive" || -n "$db_checksum" ]]; then
  db_checksum="${db_checksum#sha256:}"
  if [[ ! -f "$db_archive" || ! -r "$db_archive" || ! "$db_checksum" =~ ^[0-9a-f]{64}$ ]]; then
    echo "Set both SILLAGE_GRYPE_DB_ARCHIVE to a readable file and SILLAGE_GRYPE_DB_SHA256 to its SHA-256." >&2
    exit 2
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    actual_checksum="$(sha256sum < "$db_archive")"
  else
    actual_checksum="$(shasum -a 256 < "$db_archive")"
  fi
  if [[ "${actual_checksum%% *}" != "$db_checksum" ]]; then
    echo "Grype database archive SHA-256 mismatch." >&2
    exit 2
  fi
fi

mkdir -p "$output_dir"
output_dir="$(cd -- "$output_dir" && pwd)"

docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  "$syft_image" \
  "$source_ref" \
  -o spdx-json \
  > "$output_dir/${file_prefix}.spdx.json"

docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  "$syft_image" \
  "$source_ref" \
  -o cyclonedx-json \
  > "$output_dir/${file_prefix}.cyclonedx.json"

db_container=""
grype_container=""
cleanup() {
  if [[ -n "$grype_container" ]]; then
    docker rm -fv "$grype_container" >/dev/null || true
  fi
  if [[ -n "$db_container" ]]; then
    docker rm -fv "$db_container" >/dev/null || true
  fi
}
trap cleanup EXIT

set -- "$grype_image" "$source_ref" --fail-on "$fail_on" -o json
if [[ -n "$db_archive" ]]; then
  # Keep the imported DB on a disposable volume shared by the two containers.
  # docker cp works even when the Docker daemon is on another host.
  db_container="$(docker create --network none -v /sillage-grype-db \
    -e GRYPE_DB_CACHE_DIR=/sillage-grype-db -e GRYPE_CHECK_FOR_APP_UPDATE=false \
    "$grype_image" db import /tmp/sillage-grype-db.tar.zst)"
  docker cp "$db_archive" "$db_container:/tmp/sillage-grype-db.tar.zst"
  docker start -a "$db_container" >&2
  set -- --volumes-from "$db_container" \
    -e GRYPE_DB_CACHE_DIR=/sillage-grype-db -e GRYPE_DB_AUTO_UPDATE=false \
    -e GRYPE_DB_VALIDATE_BY_HASH_ON_START=true -e GRYPE_DB_VALIDATE_AGE=true \
    -e GRYPE_CHECK_FOR_APP_UPDATE=false "$@"
fi

grype_container="$(docker create -v /var/run/docker.sock:/var/run/docker.sock "$@")"

docker start -a "$grype_container" > "$output_dir/${file_prefix}.grype.json"

node "$repo_root/scripts/check-sbom.mjs" \
  "$output_dir/${file_prefix}.spdx.json" \
  "$output_dir/${file_prefix}.cyclonedx.json" \
  "$output_dir/${file_prefix}.grype.json"
