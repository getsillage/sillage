# Shared native UI

Reserved for Compose Multiplatform design primitives, the application shell,
and UI shared by Android, iOS, Windows, and macOS.

Platform integration and packaging do not belong in this module. Feature state
and use cases belong under `packages/kmp-features/`. Shared UI is the default,
not a lowest-common-denominator requirement: a platform host may replace a
surface with native UI while continuing to consume the same feature contract.
