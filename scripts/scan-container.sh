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

docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  "$grype_image" \
  "$source_ref" \
  --fail-on "$fail_on" \
  -o json \
  > "$output_dir/${file_prefix}.grype.json"

node "$repo_root/scripts/check-sbom.mjs" \
  "$output_dir/${file_prefix}.spdx.json" \
  "$output_dir/${file_prefix}.cyclonedx.json" \
  "$output_dir/${file_prefix}.grype.json"
