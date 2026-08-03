# Synchronization

Platform-independent synchronization contracts and native memo-sync state
transitions. The module owns `PendingMemoSync`, `AppliedMemoSync`,
`ConflictMemoSync`, `SyncPushSummary`, durable `PendingMemoMutation`, pending
resolution rules, applied-result and pulled-state merging, and
keep-local/take-server conflict transitions in `commonMain`.

`kmp-core:network` provides the shared JSON/REST mapping for memo push and pull.
retains its current transactional adapter as a migration source; iOS, Windows,
and macOS now use the shared workspace through `kmp-core:local-data`. Transport,
SQLite, and platform types must not enter this module.

`MemoSyncOutbox`, `MemoSyncWorkspace`, and `MemoSyncGateway` isolate local and
remote adapters. Pulled server records replace only clean local state; any
record with a pending mutation keeps its device value and existing cloud
baseline until push or explicit conflict resolution converges it.
`MemoSyncWorkspaceFactory` binds the outbox and conflict repository to one
normalized server address; `MemoSyncGatewayFactory` creates the matching
authenticated remote gateway. `PushPendingMemosUseCase` reads pending mutations,
skips an empty push, hands the pending set to the gateway, and acknowledges only
applied results through the transactional outbox. Remote adapters split by the
wire batch limit.

`ResolveMemoSyncConflictUseCase` dispatches explicit keep-local and take-server
commands through `MemoSyncConflictRepository`; platform hosts provide a
transactional local-storage adapter. Applied restore results preserve newer local
fields and schedule a follow-up update rather than overwriting an edit made after
local restoration. Pending conflict presentation state belongs to
`kmp-features:sync`.

`SyncSnapshot` is the platform-neutral full-pull value. It contains only
syncable domain data and an explicit available/unavailable AI-settings section;
backup format metadata and client presentation preferences are excluded.
`PullSyncUseCase` composes `SyncSnapshotGateway` and `SyncSnapshotRepository`,
requiring adapters to merge a completed snapshot atomically.

`RunSyncPushUseCase` requires platform attachment preparation before reading the
memo outbox. `RunTwoWaySyncUseCase` preserves Android's push-then-pull ordering
and returns both results for feature presentation. `RunMemoTwoWaySyncUseCase`
applies the same ordering to the shared iOS/desktop memo workspace and reports
the count of pulled records that changed local presentation. If pull fails after
push completed, `MemoSyncPullFailedException` carries that push result so the
host can present already-persisted canonical records and conflicts before it
reports the pull failure.
