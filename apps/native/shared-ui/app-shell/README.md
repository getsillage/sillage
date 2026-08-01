# Native application shell

Shared application-wide presentation state and, incrementally, Compose
navigation structure, top-level layout, feedback hosts, and feature composition.

`AppAppearanceStateHolder` owns canonical theme and interface-language values
plus their platform-neutral transitions. Hosts hydrate it from their local
preference adapter, persist accepted changes, and apply the resulting system
theme or locale. Platform lifecycle and packaging remain in each application
host.
