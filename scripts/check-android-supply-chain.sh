#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
lockfile="$repo_root/android/app/gradle.lockfile"
report_dir="$repo_root/android/app/build/reports"
report="$report_dir/osv-scanner-release.json"

scanner_version="2.4.0"
scanner_base_url="https://github.com/google/osv-scanner/releases/download/v${scanner_version}"

node "$repo_root/scripts/generate-android-third-party-notices.mjs"

case "$(uname -s):$(uname -m)" in
  Darwin:arm64)
    scanner_asset="osv-scanner_darwin_arm64"
    scanner_sha256="9ca3185ad63e9ab54f7cb90f46a7362be02d80e37f0123d095a54355ea202f5d"
    ;;
  Darwin:x86_64)
    scanner_asset="osv-scanner_darwin_amd64"
    scanner_sha256="088119325156321c34c456ac3703d6013538fd71cbac82b891ab34db491e4d66"
    ;;
  Linux:aarch64|Linux:arm64)
    scanner_asset="osv-scanner_linux_arm64"
    scanner_sha256="44e580752910f0ff36ec99aff59af20f65df1e859aa31e5605a8f0d055b496e9"
    ;;
  Linux:x86_64|Linux:amd64)
    scanner_asset="osv-scanner_linux_amd64"
    scanner_sha256="15314940c10d26af9c6649f150b8a47c1262e8fc7e17b1d1029b0e479e8ed8a0"
    ;;
  *)
    echo "Unsupported OSV Scanner platform: $(uname -s) $(uname -m)" >&2
    exit 2
    ;;
esac

if [[ ! -f "$lockfile" ]]; then
  echo "Missing Android dependency lockfile: $lockfile" >&2
  echo "Run: cd android && ./gradlew :app:dependencies --write-locks" >&2
  exit 1
fi

cache_root="${OSV_SCANNER_CACHE_DIR:-${XDG_CACHE_HOME:-/tmp}/sillage-osv-scanner}"
scanner="${OSV_SCANNER_BIN:-$cache_root/${scanner_version}/${scanner_asset}}"

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d ' ' -f 1
  else
    shasum -a 256 "$1" | cut -d ' ' -f 1
  fi
}

if [[ ! -x "$scanner" ]]; then
  if [[ -n "${OSV_SCANNER_BIN:-}" ]]; then
    echo "OSV_SCANNER_BIN is not executable: $scanner" >&2
    exit 1
  fi
  mkdir -p "$(dirname -- "$scanner")"
  scanner_download="${scanner}.download"
  trap 'rm -f "${scanner_download:-}"' EXIT
  curl --fail --silent --show-error --location \
    "$scanner_base_url/$scanner_asset" \
    --output "$scanner_download"
  actual_sha256="$(sha256_file "$scanner_download")"
  if [[ "$actual_sha256" != "$scanner_sha256" ]]; then
    echo "OSV Scanner checksum mismatch for $scanner_asset" >&2
    echo "expected: $scanner_sha256" >&2
    echo "actual:   $actual_sha256" >&2
    exit 1
  fi
  chmod 0755 "$scanner_download"
  mv "$scanner_download" "$scanner"
  trap - EXIT
fi

actual_sha256="$(sha256_file "$scanner")"
if [[ "$actual_sha256" != "$scanner_sha256" ]]; then
  echo "Cached OSV Scanner checksum mismatch: $scanner" >&2
  echo "expected: $scanner_sha256" >&2
  echo "actual:   $actual_sha256" >&2
  exit 1
fi

scan_tmp="$(mktemp -d)"
trap 'rm -rf "$scan_tmp"' EXIT
runtime_lockfile="$scan_tmp/gradle.lockfile"

{
  sed -n '1,3p' "$lockfile"
  grep -E '(^|[,=])releaseRuntimeClasspath([,]|$)' "$lockfile"
} > "$runtime_lockfile"

runtime_packages="$(grep -vc '^#' "$runtime_lockfile")"
if [[ "$runtime_packages" -eq 0 ]]; then
  echo "Android lockfile contains no releaseRuntimeClasspath packages" >&2
  exit 1
fi

mkdir -p "$report_dir"
set +e
"$scanner" scan source \
  --lockfile "$runtime_lockfile" \
  --format json \
  --output-file "$report"
scanner_status=$?
set -e

if [[ -s "$report" ]]; then
  node - "$report" "$runtime_packages" <<'NODE'
const fs = require("node:fs");

const report = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const packages = Number(process.argv[3]);
const vulnerabilities = [];

for (const result of report.results ?? []) {
  for (const item of result.packages ?? []) {
    for (const vulnerability of item.vulnerabilities ?? []) {
      vulnerabilities.push({
        id: vulnerability.id,
        package: `${item.package?.name ?? "unknown"}@${item.package?.version ?? "unknown"}`,
        severity: vulnerability.database_specific?.severity ?? "UNKNOWN",
        summary: vulnerability.summary ?? "",
      });
    }
  }
}

console.log(`OSV Scanner checked ${packages} Android release-runtime packages.`);
if (vulnerabilities.length > 0) {
  for (const vulnerability of vulnerabilities) {
    console.error(
      `${vulnerability.severity} ${vulnerability.id} ${vulnerability.package}: ${vulnerability.summary}`,
    );
  }
}
NODE
else
  echo "OSV Scanner did not produce a JSON report." >&2
fi

if [[ "$scanner_status" -ne 0 ]]; then
  if [[ "$scanner_status" -eq 1 ]]; then
    echo "OSV Scanner found known vulnerabilities in the Android release runtime." >&2
  else
    echo "OSV Scanner failed with status $scanner_status." >&2
  fi
  echo "Report: $report" >&2
  exit "$scanner_status"
fi
