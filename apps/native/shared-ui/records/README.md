# Shared records UI

Buildable Compose Multiplatform records UI shared by Android, iOS, Windows, and
macOS hosts.

`SillageRecordFilterTabs` consumes `RecordsFeatureStateHolder` directly for the
active list filter and owns the selectable-tab layout, colors, and semantics.
`SillageRecordSearchBar` consumes the aggregate search query, request state, and
published-result presence while owning IME search, progress, clear, and action UI.
`SillageRecordEmptyState` owns reusable records empty/error presentation, optional
icon treatment, and optional action layout.
`SillageRecentlyDeletedRecordRow` consumes aggregate mutation ownership and a
record while owning the row layout and two-step permanent-delete confirmation.
`SillageCalendarCoverageNotice` consumes aggregate pagination state and
`CalendarMemoCoverage` while owning partial-coverage copy selection, progress,
and the load-earlier action layout.
Hosts supply localized labels, icons, already-formatted deletion timestamps, and
mutation callbacks. Transport,
storage, navigation, native attachment handling, and platform lifecycle remain
outside the module.
