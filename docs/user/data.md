# Data, Backup, and Recovery

With the default configuration, the server's persistence unit is the complete data directory. The Docker example maps `/var/opt/sillage` in the container to `$HOME/.sillage` on the host. An explicitly configured external DSN or secret file is part of the same recovery point. Browser drafts are not included, as described below.

## Directory Contents

```text
sillage.db
sillage.db-wal
sillage.db-shm
sillage.db.sillage.lock
assets/attachments/
.thumbnail_cache/
runtime/secrets.json
runtime/instance.lock
```

- SQLite stores the account, records, AI settings, and sessions.
- `sillage.db.sillage.lock` prevents two supported processes from using the same SQLite file. A custom external DSN gets the same `.sillage.lock` sidecar next to its database path.
- `assets/attachments/` stores attachment bytes.
- `.thumbnail_cache/` is a regenerable cache.
- `runtime/secrets.json` stores automatically generated session and encryption secrets. It is not a cache.
- `runtime/instance.lock` coordinates exclusive access to the rest of the data directory. Lock sidecars are not secrets and may remain after exit; the operating-system lock, not file existence, indicates an active process.

Records, attachments, and backups do not have additional whole-dataset encryption at rest. Losing `runtime/` invalidates existing sessions and may make saved AI API keys impossible to decrypt.

When `SESSION_SECRET` / `ENCRYPTION_SECRET` or their corresponding `_FILE` variables are set explicitly, the effective runtime values are not guaranteed to be written back to `runtime/secrets.json`. These external secrets must be stored securely on their own and restored with the data. Changing `SESSION_SECRET` invalidates sessions; changing `ENCRYPTION_SECRET` makes existing AI API keys impossible to decrypt.

## Deletion and Browser Drafts

Deleting a record moves it to Recently Deleted. The record body and its related data remain available for recovery for 30 days. During that window, the Web and Android clients can restore the record or permanently delete it. A restored record returns to the normal library and receives a new version so offline clients can converge on the restored state.

Permanent deletion cannot be undone through Sillage. It immediately scrubs the record body, user-selected date, favorite/archive state, AI summary, generated summaries that cite the record, and Ask answers whose retained source references point to it. After 30 days, the server performs the same permanent-deletion operation during the next maintenance cycle, which runs at startup and every six hours. Backups made before permanent deletion may still contain the original data and must be expired under the operator's backup-retention policy.

The server retains a minimal purged tombstone containing structural synchronization metadata rather than the original body. This lets a device that has been offline longer than the recovery window learn that the record must remain deleted. Shared attachment bytes are retained while another non-purged record still references them. When the last live reference is permanently deleted, the attachment metadata is tombstoned and the maintenance task removes the server file bytes; Android also removes an unshared pending offline attachment from device storage. Deleting an AI profile separately clears the encrypted API key envelope stored in the current server database, but older backups may still contain it.

Other ephemeral data is also cleaned automatically at startup and every six hours. Expired or revoked refresh sessions and expired runtime values are removed. Sync mutation results provide a 90-day idempotency window and are deleted afterward, so a retry older than 90 days must be treated as a new reconciliation event rather than a guaranteed replay. Deleted attachment metadata remains as a tombstone, while its file bytes are removed; unreferenced attachment files older than 24 hours are also removed to recover from interrupted uploads or metadata writes.

To recover unsaved records and quick captures, the Web app stores the draft content, date, and baseline version in plaintext browser `localStorage`. Drafts are not included in server backups and may remain in the same browser profile after sign-out. Avoid using the Web app on a shared device, or save or discard drafts and clear the site's browser data before leaving.

After signing in, change the account password in Settings under Account (`账号`). Changing the password keeps all records and other data under the same account, issues a new session for the client that completed the change, and ends other refresh sessions. There is no unauthenticated forgot-password endpoint. Store the password in a password manager. If you forget it, do not delete the data directory in an attempt to initialize the instance again, because doing so breaks the relationship between the existing data and account; use the offline procedure below.

## Offline Password Recovery

The local `admin reset-password` command is the break-glass recovery path for an operator who already controls the complete data directory. It updates the password hash and revokes every refresh session in one SQLite transaction. Existing access tokens can remain valid for at most 15 minutes, so keep the service stopped until the command succeeds and then restart it. The command acquires the same data-directory lock as the server and refuses to run if another Sillage process is active.

The new password must be supplied through a regular, non-symlink file. On Unix, the file must have no group or other permissions (mode `0600` is recommended). A single trailing newline is removed; additional lines are rejected. Do not put the password in a command argument, environment variable, shell history, or shared temporary directory.

For Compose, stop the service and run the recovery command as the image's normal UID. This example reads the password without echoing it, creates the restricted file inside the mounted runtime directory, and removes it on exit:

```bash
docker compose -f scripts/compose.yaml stop sillage
read -r -s NEW_PASSWORD && printf '\n'
if printf '%s\n' "$NEW_PASSWORD" | docker compose -f scripts/compose.yaml run --rm -T sillage \
  sh -eu -c '
      umask 077
      password_file=/var/opt/sillage/runtime/reset-password
      trap "rm -f $password_file" EXIT
      cat > "$password_file"
      /usr/local/sillage/sillage admin reset-password \
        --username YOUR_USERNAME \
        --password-file "$password_file"
    '
then
  unset NEW_PASSWORD
  docker compose -f scripts/compose.yaml start sillage
else
  unset NEW_PASSWORD
  printf 'Password reset failed; Sillage remains stopped.\n' >&2
fi
```

For a local binary, use the same data directory and DSN as the stopped service:

```bash
umask 077
PASSWORD_FILE="$(mktemp)"
trap 'rm -f "$PASSWORD_FILE"' EXIT
read -r -s NEW_PASSWORD && printf '\n'
printf '%s\n' "$NEW_PASSWORD" > "$PASSWORD_FILE"
unset NEW_PASSWORD
./sillage --data "$HOME/.sillage" admin reset-password \
  --username YOUR_USERNAME \
  --password-file "$PASSWORD_FILE"
```

If the instance uses a custom `SILLAGE_DSN`, pass the same value or `_FILE` setting to the command. Back up the instance before recovery when practical. After restart, sign in with the new password. Previously issued access tokens may continue working until their 15-minute lifetime ends; after that, confirm that old devices must authenticate again.

## Native Client Portable Backups

iOS, Windows, and macOS can export and restore a JSON record backup through the
native file picker. The file contains the portable record and appearance subset
and remains sensitive plaintext; store and transfer it through protected media.
It does not contain the account, access or refresh credentials, checked server
address, server binding, cloud versions, pending mutation identifiers, Ask data,
or server attachment bytes.

Restore validates the complete file before replacing readable local records. It
preserves the device's current server address and any preference omitted by the
backup, but deliberately clears the private sync binding, cloud baselines, and
outbox. Consequently, restored records remain local changes until a later
manual synchronization pushes them before pulling current server records; do
not assume the JSON file is a continuation of an existing server queue. A failed
or unsupported import leaves current local state intact.
This portable file is not a substitute for the complete server data-directory
backup below.

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

Android and shared native JSON exports and manual synchronization do not include
server attachment bytes, the account, sessions, or runtime secrets. They cannot
replace a complete server data-directory backup.

## Recovery Objectives and Drills

For a typical personal deployment, use a backup schedule that targets an RPO of 24 hours or less and an RTO of 2 hours or less. These are operator targets, not guarantees from the application: the actual data-loss window equals the time since the last complete, readable backup, and restore time depends on data size and host availability.

Run a restore drill at least quarterly and before a high-risk upgrade. Restore the latest backup into an isolated directory or host, start one Sillage instance against it, then verify SQLite integrity, sign-in, representative records, search, attachments, AI settings, and sync bootstrap. Record the backup timestamp, restore duration, result, and any missing external secret or DSN dependency. Do not point the drill instance and production instance at the same SQLite files.

Maintainers can run the repository's disposable recovery drill with `make check-restore`. It creates a temporary instance, writes a representative record, attachment, encrypted AI profile, and summary, copies the complete stopped data directory, creates a post-backup change, restores with a preserved rollback directory, restarts the service, and verifies the checks above through HTTP plus SQLite integrity checks. `make check-upgrade` separately builds the latest stable tag, creates representative data with that binary, starts the candidate against the copied data, verifies the schema and user journeys, proves that the old binary rejects the upgraded schema, and then restores the complete pre-upgrade backup before starting the old binary again. Both drills use isolated temporary directories and never read or modify an operator's data directory.
