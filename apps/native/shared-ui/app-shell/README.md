# Native application shell

Shared application-wide presentation state and, incrementally, Compose
navigation structure, top-level layout, feedback hosts, and feature composition.

`AppAppearanceStateHolder` owns canonical theme and interface-language values
plus their platform-neutral transitions. Hosts hydrate it from their local
preference adapter, persist accepted changes, and apply the resulting system
theme or locale. `AppDestination` and `AppNavigationPolicy` own native root
destination identity, history updates, fallback back navigation, and the rule
that secondary destinations return to Records before the host exits.
`AppClientContextStateHolder` aggregates destination, history, application
mode, workspace generation, and the server-settings return destination. It
owns pure mode selection, workspace switching, server return, sign-out reset,
and navigation transitions. Hosts persist accepted preferences and execute
network connections, cancellation, system Back dispatch, and other platform
effects.
`AppWorkspaceStateHolder` aggregates the records, settings, and Ask feature
holders. Its `clearClientWorkspace` and `enterOfflineClientWorkspace`
transitions update all three holders together, so hosts cannot expose a
partially reset or partially hydrated interactive workspace. Repository work,
cancellation, persistence, and other side effects remain host responsibilities.
`AppFeedbackEventEmitter` owns one-shot event IDs, error precedence, duplicate
suppression, notice severity, and language binding. Platform lifecycle, system
Back dispatch, Toast rendering, and packaging remain in each application host.

Android theme/language orchestration, Compose screens, localized feedback, and
tests consume `appearance`, `clientContext`, and `workspace` directly, without
root compatibility getters.
