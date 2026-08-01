# Kotlin Multiplatform features

Feature-scoped state and presentation logic for authentication, records, Ask,
settings, and synchronization. Each feature owns its state holder and must not
contribute a single application-wide ViewModel.

Buildable modules currently include `auth`, `records`, `ask`, `settings`, and
`sync`. The auth module owns credential drafts and context-safe password-change
state; the Ask module owns conversation selection, branch-head identity, and
loaded message snapshots. Platform adapters retain secure session persistence,
protocol mapping, persistence, and streaming transports.
