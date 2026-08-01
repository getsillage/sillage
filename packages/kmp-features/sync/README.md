# Synchronization feature

Shared feature-scoped synchronization presentation state. The first buildable
slice owns `MemoSyncConflictStateHolder` and `MemoSyncConflictItem`, including
conflict identity, local/server presentation values, replacement, dismissal,
and removal transitions.

The synchronization algorithm, snapshot contracts, repository ports, and use
cases belong to `packages/kmp-core/sync/`. This module depends on those contracts
but must not perform transport, persistence, or platform UI work.
