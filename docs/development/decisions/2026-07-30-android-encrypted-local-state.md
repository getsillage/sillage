# ADR: Transactional encrypted Android local state

- Status: Accepted
- Date: 2026-07-30

## Context

The Android offline client originally serialized records, Ask history, AI configuration, attachment metadata, and synchronization state into a small set of SharedPreferences values. The sensitive values were encrypted with Android Keystore, but a growing library still required decrypting and rewriting a large JSON value. SharedPreferences also could not make record and sync-metadata updates atomic across keys, and the project had no real Android Runtime evidence for Keystore migration or persistence after process recreation.

Treating an unreadable encrypted value as missing would be especially dangerous: a lost Keystore key or damaged payload could appear to be an empty library and later be overwritten.

## Decision

Keep the `LocalDataStore` business API and replace its persistence implementation with `LocalStateStore`:

- SQLite stores one encrypted value per logical state key in `state_values` and uses WAL mode.
- Android Keystore holds a non-exportable 256-bit AES key. Each value uses a fresh AES-GCM IV and carries a versioned ciphertext envelope.
- Operations that change content together with cloud versions or pending mutations use one SQLite transaction.
- First open reads the former `sillage.local_data` SharedPreferences keys, writes only database keys that do not already exist, commits the database transaction, and then removes the legacy copies. Reopening safely retries an interrupted migration.
- Plaintext legacy values are encrypted during migration. Unreadable encrypted values are copied without modification, surfaced as corruption on read, and preserved for diagnosis.
- Android automatic backup remains disabled. Explicit JSON export is the portability mechanism and remains sensitive plaintext after API-key redaction.
- Robolectric tests cover migration, reopen persistence, atomic writes, and unreadable ciphertext. Instrumentation tests use the real Android Keystore and SQLite implementation and cover a cold Activity relaunch.

## Consequences

Large offline libraries no longer require rewriting every logical state value, and sync metadata cannot commit independently from the content transition it describes. Database keys and row counts remain visible to someone with application-private filesystem access, but user values in the database and WAL are ciphertext; this is field encryption, not a claim of full database or device encryption.

The schema now requires explicit, forward-compatible database migrations before `DATABASE_VERSION` changes. Losing the Android Keystore key makes existing ciphertext unrecoverable by design, so code must continue to fail closed and must not offer a destructive automatic reset. Session tokens and bounded interface preferences remain in `SessionStore` because they do not share the unbounded offline-library or multi-key transaction requirements.

SQLCipher was not selected for this change because it adds a native database runtime and migration boundary while the current threat model is satisfied by Keystore-backed value encryption plus Android application sandboxing. Revisit whole-database encryption only with a documented recovery model, native dependency maintenance plan, and device migration evidence.
