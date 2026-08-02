# Shared synchronization UI

Buildable Compose Multiplatform synchronization UI shared by Android, iOS,
Windows, and macOS hosts.

`SillageSyncConflictDialog` consumes `SyncFeatureStateHolder` directly and owns
selection of the first open conflict, local/server preview fallback and length
limits, scrollable dialog layout, and resource-ID action routing. Hosts supply
localized strings and resolution callbacks.

Synchronization orchestration, transactional conflict storage, transport,
platform lifecycle, and native feedback rendering remain outside this module.
