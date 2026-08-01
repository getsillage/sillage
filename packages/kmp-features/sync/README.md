# Synchronization feature

Shared feature-scoped synchronization presentation state.

`SyncFeatureStateHolder` is the feature aggregate. It currently composes
`MemoSyncConflictStateHolder` / `MemoSyncConflictItem` and owns conflict
replacement, dismissal, lookup, and push-result application so hosts do not keep
conflict presentation as a loose top-level root field.
Android routes push-result application, conflict dismissal, and conflict-list
replacement through root `withSync` thin wrappers.

The synchronization algorithm, snapshot contracts, repository ports, and use
cases belong to `packages/kmp-core/sync/`. This module depends on those contracts
but must not perform transport, persistence, or platform UI work.
