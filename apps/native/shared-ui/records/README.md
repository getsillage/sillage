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
`SillageRecordsContent` consumes `RecordsFeatureStateHolder` directly and owns
list-mode filter/search composition, pull-to-refresh, initial loading, failure
selection, and list/calendar body switching. Hosts provide localized strings,
icons, search announcements, locale-aware calendar content, and record-row slots.
`SillageRecordList` consumes `RecordsFeatureStateHolder` directly and owns search
result selection, filter-specific empty states, On This Day visibility, lazy-list
composition, active/deleted row branching, and pagination action presentation.
`SillageRecordSummarySection` consumes shared `MemoAI` presentation and owns the
summary card, loading/action copy selection, published-body fallback, and
provider/model/source/token metadata layout. Hosts format localized plural labels.
`SillageRecordDetailCard` owns entry-date/status layout, lifecycle status joining,
content separation, and blank-record fallback while hosts provide localized labels
and the Markdown/content slot. `SillageRecordStatusLine` reuses the same status
selection for editor and other host surfaces.
`SillageRecordMetadataBlock` owns revision-count derivation, divider treatment,
and created/updated label layout while hosts format localized timestamps and
revision plurals.
`SillageRecordDetailContent` owns missing-record fallback, detail-section ordering,
lazy-list spacing, and shared content width while hosts fill record, summary, and
metadata adapter slots.
`SillageRecordDetailActions` consumes aggregate selection/mutation state and
owns edit/more action enablement, lifecycle-aware favorite/archive menu choices,
mutation-driven menu reset, and delete confirmation. Hosts supply the
host-operation gate, localized strings, icons, and mutation/navigation callbacks.
`SillageRecordEditorActions` consumes aggregate selection/editor/mutation state
plus `RecordsEditorActionContext` and owns save progress semantics,
attachment-vs-save busy copy, existing-record lifecycle menu choices, and editor
delete confirmation. Hosts supply localized online/offline copy, icons, callbacks,
and the destination/global-operation context.
`rememberSillageRecordEditorCloseRequest` consumes aggregate editor state plus
`RecordsEditorActionContext` and owns dirty-draft close selection and
discard-confirmation state/dialog while hosts retain platform Back integration,
localized copy, and the close callback.
`SillageRecordEditorContent` owns editor lazy-list section ordering, shared
content width, lifecycle status, date field, attachment action, and existing-record
summary visibility. Hosts provide localized strings/icons, date and attachment
callbacks, plus Markdown-editor and summary slots.
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
`SillageRecordCalendar` consumes `RecordsFeatureStateHolder` directly for date
counts, selected records, month coverage, empty-selection state, and calendar-list
composition while hosts provide locale-aware grid/header and record-row adapters.
`SillageOnThisDayCard` consumes record entries and the current date while owning
anniversary calculation, excerpts, dividers, and record selection.
Hosts supply localized labels, icons, already-formatted deletion timestamps, and
mutation callbacks; anniversary copy is supplied through a localized formatter.
`SillageRecordsTopBarTitle`, `SillageRecordsRefreshAction`, and
`SillageRecordsNewRecordAction` own records/calendar title selection, heading
layout, refresh request gates, deleted-filter FAB visibility, and action layout.
Hosts provide the localized subtitle/resources and retain the Scaffold shell.

Transport,
storage, navigation, native attachment handling, and platform lifecycle remain
outside the module.
