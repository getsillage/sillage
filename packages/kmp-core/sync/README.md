# Synchronization

Platform-independent synchronization contracts and, incrementally, the native
sync state machine. The first buildable slice owns `PendingMemoSync`,
`AppliedMemoSync`, `ConflictMemoSync`, and `SyncPushSummary` in `commonMain`.

Android currently provides JSON/REST mapping, transactional outbox persistence,
attachment staging, and the transactional conflict-resolution adapter. Those are
migration sources for later ports and state-machine slices; transport, SQLite,
and platform types must not enter this module.

`MemoSyncOutbox` and `MemoSyncGateway` isolate local and remote adapters.
`PushPendingMemosUseCase` reads pending mutations, skips an empty push, sends one
batch, and acknowledges only applied results through the transactional outbox.

`ResolveMemoSyncConflictUseCase` dispatches explicit keep-local and take-server
commands through `MemoSyncConflictRepository`; platform hosts provide a
transactional local-storage adapter. Pending conflict presentation state belongs
to `kmp-features:sync`.

`SyncSnapshot` is the platform-neutral full-pull value. It contains only
syncable domain data and an explicit available/unavailable AI-settings section;
backup format metadata and client presentation preferences are excluded.
`PullSyncUseCase` composes `SyncSnapshotGateway` and `SyncSnapshotRepository`,
requiring adapters to merge a completed snapshot atomically.

`RunSyncPushUseCase` requires platform attachment preparation before reading the
memo outbox. `RunTwoWaySyncUseCase` preserves the push-then-pull ordering and
returns both results for feature presentation.
