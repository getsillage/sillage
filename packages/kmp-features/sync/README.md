# Synchronization feature

Shared feature-scoped synchronization presentation state.

`SyncFeatureStateHolder` is the feature aggregate. It currently composes
`MemoSyncConflictStateHolder` / `MemoSyncConflictItem` and owns conflict
replacement, dismissal, lookup, and push-result application so hosts do not keep
conflict presentation as a loose top-level root field.
Android routes push-result application, conflict dismissal, and conflict-list
replacement through root `withSync` thin wrappers. Conflict-resolution callbacks
look up the current item through `SyncFeatureStateHolder.findConflict`.

The buildable `shared-ui:sync` module consumes the aggregate directly for the
conflict dialog, including first-open-conflict selection and resource-ID action
routing. Platform hosts retain localized strings and execute resolution effects.

The synchronization algorithm, snapshot contracts, repository ports, and use
cases belong to `packages/kmp-core/sync/`. This module depends on those contracts
but must not perform transport, persistence, or platform UI work.
