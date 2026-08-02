# Shared records UI

Buildable Compose Multiplatform records UI shared by Android, iOS, Windows, and
macOS hosts.

`SillageRecordFilterTabs` consumes `RecordsFeatureStateHolder` directly for the
active list filter and owns the selectable-tab layout, colors, and semantics.
`SillageRecordSearchBar` consumes the aggregate search query, request state, and
published-result presence while owning IME search, progress, clear, and action UI.
`SillageRecordSearchStatus` owns completed-search status layout, semantics, and
per-completion announcement deduplication from host-formatted summary copy.
`SillageRecordEmptyState` owns reusable records empty/error presentation, optional
icon treatment, and optional action layout.
`SillageRecordList` consumes `RecordsFeatureStateHolder` directly and owns search
result selection, filter-specific empty states, On This Day visibility, lazy-list
composition, active/deleted row branching, and pagination action presentation.
`SillageRecordRow` consumes a record plus host-localized labels and owns card
layout, blank-content fallback, status presentation, long-click semantics, and
mutation progress.
`SillageRecordSwipeActionPane` owns favorite/archive action layout, state-based
labels and icons, revealed-side visibility, and hidden-action semantics.
`SillageRecordQuickActionsSheet` owns record excerpts, state-based action copy,
bottom-sheet layout, destructive styling, and two-step delete confirmation.
`SillageRecordSwipeRow` composes the shared row, revealed actions, and quick-action
sheet while owning drag bounds, settle thresholds, mutation reset, and action close.
`SillageRecentlyDeletedRecordRow` consumes aggregate mutation ownership and a
record while owning the row layout and two-step permanent-delete confirmation.
`SillageCalendarCoverageNotice` consumes aggregate pagination state and
`CalendarMemoCoverage` while owning partial-coverage copy selection, progress,
and the load-earlier action layout.
`SillageCalendarEmptySelection` consumes the same coverage value and selects the
definitive-empty or possibly-incomplete calendar message.
`SillageCalendarHeader` owns month navigation layout and semantics from
host-formatted month labels and platform-provided directional icons.
`SillageCalendarGrid` owns weekday/date rows, record counts, today/selection
styling, and complete day semantics from host-formatted labels and descriptions.
`SillageOnThisDayCard` consumes record entries and the current date while owning
anniversary calculation, excerpts, dividers, and record selection.
Hosts supply localized labels, icons, already-formatted deletion timestamps, and
mutation callbacks; anniversary copy is supplied through a localized formatter.
Transport,
storage, navigation, native attachment handling, and platform lifecycle remain
outside the module.
