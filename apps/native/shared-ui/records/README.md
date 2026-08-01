# Shared records UI

Buildable Compose Multiplatform records UI shared by Android, iOS, Windows, and
macOS hosts.

`SillageRecordFilterTabs` consumes `RecordsFeatureStateHolder` directly for the
active list filter and owns the selectable-tab layout, colors, and semantics.
`SillageRecordSearchBar` consumes the aggregate search query, request state, and
published-result presence while owning IME search, progress, clear, and action UI.
`SillageRecordEmptyState` owns reusable records empty/error presentation, optional
icon treatment, and optional action layout.
Hosts supply localized labels and the filter mutation callback. Transport,
storage, navigation, native attachment handling, and platform lifecycle remain
outside the module.
