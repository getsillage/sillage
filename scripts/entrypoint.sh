#!/usr/bin/env sh
set -eu

SILLAGE_UID="${SILLAGE_UID:-10001}"
SILLAGE_GID="${SILLAGE_GID:-10001}"
SILLAGE_DATA="${SILLAGE_DATA:-/var/opt/sillage}"

file_env() {
  var="$1"
  file_var="${var}_FILE"
  val="$(printenv "$var" 2>/dev/null || true)"
  file_val="$(printenv "$file_var" 2>/dev/null || true)"

  if [ -n "$val" ] && [ -n "$file_val" ]; then
    echo "error: both $var and $file_var are set" >&2
    exit 1
  fi
  if [ -n "$file_val" ]; then
    if [ ! -r "$file_val" ]; then
      echo "error: file '$file_val' does not exist or is not readable" >&2
      exit 1
    fi
    secret_value="$(cat "$file_val")"
    export "$var=$secret_value"
    unset "$file_var"
  fi
}

file_env "SILLAGE_DSN"
file_env "SESSION_SECRET"
file_env "ENCRYPTION_SECRET"

mkdir -p "$SILLAGE_DATA" \
  "$SILLAGE_DATA/assets/attachments" \
  "$SILLAGE_DATA/.thumbnail_cache" \
  "$SILLAGE_DATA/runtime"

if [ "$(id -u)" = "0" ]; then
  chown -R "$SILLAGE_UID:$SILLAGE_GID" "$SILLAGE_DATA" 2>/dev/null || true
  exec su-exec "$SILLAGE_UID:$SILLAGE_GID" "$0" "$@"
fi

exec "$@"
