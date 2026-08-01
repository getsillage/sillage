# Synchronization

Platform-independent synchronization contracts and, incrementally, the native
sync state machine. The first buildable slice owns `PendingMemoSync`,
`AppliedMemoSync`, `ConflictMemoSync`, and `SyncPushSummary` in `commonMain`.

Android currently provides JSON/REST mapping, transactional outbox persistence,
attachment staging, and conflict resolution. Those are migration sources for
later ports and state-machine slices; transport, SQLite, and platform types must
not enter this module.

`MemoSyncOutbox` and `MemoSyncGateway` isolate local and remote adapters.
`PushPendingMemosUseCase` reads pending mutations, skips an empty push, sends one
batch, and acknowledges only applied results through the transactional outbox.
