# Shared records UI

Buildable Compose Multiplatform records UI shared by Android, iOS, Windows, and
macOS hosts.

`SillageRecordFilterTabs` consumes `RecordsFeatureStateHolder` directly for the
active list filter and owns the selectable-tab layout, colors, and semantics.
Hosts supply localized labels and the filter mutation callback. Transport,
storage, navigation, native attachment handling, and platform lifecycle remain
outside the module.
