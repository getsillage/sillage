# Kotlin Multiplatform local data

`local-data` owns the portable native-client snapshot codec, local record
repository, and credential-free backup envelope. Platform hosts provide atomic
string storage plus time and identity values; this module owns schema validation,
domain mapping, optimistic version checks, record lifecycle rules, client
preference persistence, and validate-before-replace backup restoration.

The private `schemaVersion` snapshot and user-facing `formatVersion` backup are
separate contracts. A failed or unsupported backup never replaces the readable
local snapshot.

Backup v1 reuses Android's `formatVersion`, `exportedAt`, `themeMode`, and `memos`
vocabulary. Native hosts restore the records/appearance subset, ignore unrelated
Android-only sections, and preserve a current preference when the backup omits it.

`MigratingClientSnapshotStorage` lets a host move legacy persistence without
weakening recovery: the migration source is cleared only after the primary
adapter has accepted the complete value.

It intentionally does not own a filesystem path, `NSUserDefaults`, Keychain,
Windows Credential Manager, or Android storage APIs.
