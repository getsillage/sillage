# Kotlin Multiplatform local data

`local-data` owns the portable native-client snapshot codec, local record
repository, credential-free backup envelope, and the server-bound memo sync
workspace used by iOS, Windows, and macOS. Platform hosts provide atomic string
storage plus time, record identity, and mutation identity values; this module owns
schema validation, domain mapping, optimistic version checks, record lifecycle
rules, client preference persistence, validate-before-replace backup restoration,
and atomic record-plus-outbox writes. Pull merges records and cloud baselines
through the same atomic snapshot replacement while preserving every record with
a pending mutation.

Private snapshot schema 2 persists preferences, records, a normalized sync-server
binding, cloud versions, and pending mutation markers. Schema 1 remains readable
and upgrades on the next write. A bound outbox cannot be opened for another server
URL, preventing pending changes from being sent to a different instance. The
private `schemaVersion` snapshot and user-facing `formatVersion` backup remain
separate contracts. A failed or unsupported backup never replaces local state. A
fully validated backup may replace an unreadable private snapshot; when an import
omits a preference and current state cannot be decoded, default native preference
values are used.

Backup v1 reuses Android's `formatVersion`, `exportedAt`, `themeMode`, and `memos`
vocabulary. Native hosts restore the records/appearance subset, ignore unrelated
Android-only sections, preserve the current server preference, and deliberately
clear cloud baselines plus the private outbox. Sync metadata and server bindings
never enter the portable backup.

`MigratingClientSnapshotStorage` lets a host move legacy persistence without
weakening recovery: the migration source is cleared only after the primary
adapter has accepted the complete value.

It intentionally does not own a filesystem path, `NSUserDefaults`, Keychain,
Windows Credential Manager, or Android storage APIs.
