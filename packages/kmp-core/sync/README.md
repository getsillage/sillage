# Synchronization

Platform-independent synchronization contracts and, incrementally, the native
sync state machine. The first buildable slice owns `PendingMemoSync`,
`AppliedMemoSync`, `ConflictMemoSync`, and `SyncPushSummary` in `commonMain`.

Android currently provides JSON/REST mapping, transactional outbox persistence,
attachment staging, and conflict resolution. Those are migration sources for
later ports and state-machine slices; transport, SQLite, and platform types must
not enter this module.
