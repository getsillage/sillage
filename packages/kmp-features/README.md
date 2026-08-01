# Kotlin Multiplatform features

Reserved for feature-scoped state and presentation logic for authentication,
records, Ask, settings, and synchronization. Each feature owns its state holder
and must not contribute to a single application-wide ViewModel.

Buildable modules currently include `records`, `ask`, `settings`, and `sync`. The Ask module's
first slice owns conversation selection, branch-head identity, and loaded message
snapshots while platform adapters retain persistence and streaming transports.
