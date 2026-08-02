# Native application shell

Shared application-wide presentation state and, incrementally, Compose
navigation structure, top-level layout, feedback hosts, and feature composition.

`AppAppearanceStateHolder` owns canonical theme and interface-language values
plus their platform-neutral transitions. Hosts hydrate it from their local
preference adapter, persist accepted changes, and apply the resulting system
theme or locale. `AppDestination` and `AppNavigationPolicy` own native root
destination identity, history updates, fallback back navigation, and the rule
that secondary destinations return to Records before the host exits.
`AppFeedbackEventEmitter` owns one-shot event IDs, error precedence, duplicate
suppression, notice severity, and language binding. Platform lifecycle, system
Back dispatch, Toast rendering, and packaging remain in each application host.

Android theme/language orchestration, Compose screens, localized feedback, and
tests consume `appearance` directly, without root appearance compatibility getters.
