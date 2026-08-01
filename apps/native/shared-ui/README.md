# Shared native UI

Owns Compose Multiplatform design primitives, application shell, and UI shared
by Android, iOS, Windows, and macOS. The buildable `app-shell` module owns
application-wide presentation state and policy. The buildable `design-system`
module owns semantic theme tokens, the common `MaterialTheme`, and reusable
navigation, settings, and error-state components; Android consumes them through
thin host adapters.

Platform integration and packaging do not belong in this module. Feature state
and use cases belong under `packages/kmp-features/`. Shared UI is the default,
not a lowest-common-denominator requirement: a platform host may replace a
surface with native UI while continuing to consume the same feature contract.
