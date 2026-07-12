# Data, Backup, and Recovery

With the default configuration, the server's persistence unit is the complete data directory. The Docker example maps `/var/opt/sillage` in the container to `$HOME/.sillage` on the host. An explicitly configured external DSN or secret file is part of the same recovery point. Browser drafts are not included, as described below.

## Directory Contents

```text
sillage.db
sillage.db-wal
sillage.db-shm
assets/attachments/
.thumbnail_cache/
runtime/secrets.json
```

- SQLite stores the account, records, AI settings, and sessions.
- `assets/attachments/` stores attachment bytes.
- `.thumbnail_cache/` is a regenerable cache.
- `runtime/secrets.json` stores automatically generated session and encryption secrets. It is not a cache.

Records, attachments, and backups do not have additional whole-dataset encryption at rest. Losing `runtime/` invalidates existing sessions and may make saved AI API keys impossible to decrypt.

When `SESSION_SECRET` / `ENCRYPTION_SECRET` or their corresponding `_FILE` variables are set explicitly, the effective runtime values are not guaranteed to be written back to `runtime/secrets.json`. These external secrets must be stored securely on their own and restored with the data. Changing `SESSION_SECRET` invalidates sessions; changing `ENCRYPTION_SECRET` makes existing AI API keys impossible to decrypt.

## Deletion and Browser Drafts

Record deletion uses tombstones so offline clients can converge. The server's SQLite database retains the content of deleted records, related AI-derived data, and the deletion time. Deleting an AI profile clears the encrypted API key envelope stored in the current server database, but older backups may still contain it. There is currently no automatic cleanup for record or AI history and no workflow for permanently deleting an individual item, so this content remains in the server data directory and in backups created from it.

To recover unsaved records and quick captures, the Web app stores the draft content, date, and baseline version in plaintext browser `localStorage`. Drafts are not included in server backups and may remain in the same browser profile after sign-out. Avoid using the Web app on a shared device, or save or discard drafts and clear the site's browser data before leaving.

There is no built-in workflow for changing or resetting the account password, or for recovering access while preserving data. Store the password in a password manager. If you forget it, do not delete the data directory in an attempt to initialize the instance again, because doing so breaks the relationship between the existing data and account.

## Back Up

The following script is intended for Compose. If you use `docker run`, systemd, or a local binary, replace the stop and start commands with the appropriate equivalents and confirm that no process continues to write to SQLite. If a preflight check fails, the service remains running. If a later step fails after `docker compose stop` succeeds, the script exits and leaves the service stopped.

```bash
sh -eu <<'SH'
DATA="$HOME/.sillage"
BACKUP="$HOME/.sillage-backups/sillage-$(date +%Y%m%d-%H%M%S)"

test -f "$DATA/sillage.db"
test -d "$DATA/assets/attachments"
test -r "$DATA/runtime/secrets.json"
docker compose -f scripts/compose.yaml stop sillage
umask 077
mkdir -p "$(dirname "$BACKUP")"
test ! -e "$BACKUP"
cp -a "$DATA" "$BACKUP"
test -f "$BACKUP/runtime/secrets.json"
test "$(sqlite3 -readonly "$BACKUP/sillage.db" "PRAGMA integrity_check;")" = "ok"
docker compose -f scripts/compose.yaml start sillage
SH
```

This script requires `sqlite3` to be installed on the host. The container manages files as UID/GID `10001` by default. If the host user cannot read the secrets, use a backup account with sufficient access or configure matching UID/GID values; do not bypass permissions with `chmod 777`. Do not copy only `sillage.db`: WAL/SHM files, attachments, and runtime secrets may all live outside the database file. If `SILLAGE_DSN` points outside the data directory, you must also back up that database and its WAL/SHM files while the service is stopped.

## Verify a Backup

Before restoring, confirm at minimum that the critical paths exist:

```bash
test -f "$BACKUP/sillage.db"
test -d "$BACKUP/assets/attachments"
test -f "$BACKUP/runtime/secrets.json"
test "$(sqlite3 -readonly "$BACKUP/sillage.db" "PRAGMA integrity_check;")" = "ok"
```

The final command requires `sqlite3` to be installed on the host. Store backups outside the data directory and transfer them only through protected media.

## Restore

The restore procedure preserves the current data as a rollback copy instead of deleting it:

```bash
sh -eu <<'SH'
DATA="$HOME/.sillage"
BACKUP="$HOME/.sillage-backups/sillage-YYYYMMDD-HHMMSS"
ROLLBACK="$HOME/.sillage.before-restore-$(date +%Y%m%d-%H%M%S)"

test -d "$DATA"
test -f "$BACKUP/sillage.db"
test -d "$BACKUP/assets/attachments"
test -f "$BACKUP/runtime/secrets.json"
test "$(sqlite3 -readonly "$BACKUP/sillage.db" "PRAGMA integrity_check;")" = "ok"
test ! -e "$ROLLBACK"
docker compose -f scripts/compose.yaml stop sillage
mv "$DATA" "$ROLLBACK"
cp -a "$BACKUP" "$DATA"
docker compose -f scripts/compose.yaml start sillage
curl --fail http://localhost:5231/readyz
SH
```

Only remove or otherwise handle `ROLLBACK` after confirming that sign-in, records, and attachments all work correctly. If the restore fails, stop the service, move the failed data directory aside, and move `ROLLBACK` back to its original path. This procedure assumes the default DSN and automatically generated runtime secrets. External databases and external secrets must be restored to the same values they had when the backup was created.

## Migrate an Instance

To move an instance to another directory or host:

1. Stop both the source and destination instances.
2. Copy the complete data directory while preserving file permissions.
3. Confirm that the database, attachments, and `runtime/` are all present.
4. Configure the destination instance to use the new directory, then check `/readyz` before opening it to traffic.
5. Do not allow two instances to write to the same SQLite data.

`.thumbnail_cache/` is currently only a reserved directory; the server recreates it as an empty directory at startup. The database, attachments, and `runtime/` cannot be reset independently.

Android JSON exports and manual synchronization do not include server attachment bytes, the account, sessions, or runtime secrets. They cannot replace a complete server data-directory backup.
