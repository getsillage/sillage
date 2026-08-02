# Records feature

Shared record-list policy and editor Markdown snippet helpers.

`MarkdownFormatStyle` / `markdownFormatSnippet` provide platform-neutral editor
toolbar inserts.

Shared record-list policy and immutable feature state derived from presentation
data. The module owns mutually exclusive list filters, deterministic ordering,
On This Day calendar selectors, excerpts, and pagination-coverage decisions.

- `RecordsPaginationStateHolder` owns cursor, single-flight, loading,
  completion, failure, and cancellation transitions for load-more requests.
- `RecordsRefreshStateHolder` owns snapshot replacement status and request
  identity independently from pagination.
- `RecordsSearchStateHolder` owns query input, results, failure binding,
  completion events, and request identity.
- Records surface selectors choose list-load and search failure visibility from
  the aggregate without depending on Android root state.
- `RecordsSelectionStateHolder` owns the selected domain record and validates
  detail responses against source, destination, session, cache, and version
  generations.
- `RecordsSummaryStateHolder` owns AI-derived summary presentation, loading,
  request identity, and detail/editor context validation.
- `RecordsEditorStateHolder` owns editor sessions, draft and initial snapshots,
  dirty state, Markdown preview, and attachment-upload request ownership.
- `RecordsEditorActionPolicy` combines editor, selection, and mutation state with
  host destination/operation context to decide unsaved-draft and Back-blocking
  behavior; hosts map its reason to localized feedback.
- `RecordsMutationStateHolder` owns the active record identities for concurrent
  mutation single-flight presentation state.
- `RecordsCollectionStateHolder` owns the visible record cache and its canonical
  mutation generation.
- `RecordsBrowseStateHolder` owns list/calendar mode, semantic filtering,
  calendar month/day selection, and persisted view-mode restoration that does
  not invent an interactive filter change.
- `RecordsAttachmentOpenStateHolder` owns prepared attachment-open request
  identity, start/completion, and invalidation while platform hosts stage bytes
  and launch native viewers.
- `RecordsFeatureStateHolder` composes the holders above and owns coordinated
  multi-holder transitions for the visible list surface (clear/reset/replace/
  append, pre-refresh loading marks, and pagination cancel/stop-loading-more),
  browse filter/view-mode/calendar selection, interactive workspace teardown,
  detail/editor presentation (present/clear/save/return selected memo, begin
  editor drafts, update draft/Markdown presentation, own attachment-upload,
  attachment-open, and record-mutation presentation transitions, accept detail
  requests, update/clear search presentation, finish/complete detail summary,
  apply restored view preference after full-data import, absorb a source memo
  into cache/search, and replace a conflict-selected memo), and
  canonical memo application (cache mutation plus load/search/selection
  invalidation). Individual holders remain the unit of request identity; the
  aggregate prevents hosts from updating those slices out of lockstep.

All asynchronous holders validate captured source, client context, filter,
cache generation, and request identity before accepting a response. Pagination
also validates cursor; refresh validates the active pagination generation; and
search binds published results to the normalized query.

The module depends only on `kmp-core:domain`. It must not perform transport,
storage, synchronization, or platform UI work. Android stores one
`RecordsFeatureStateHolder` on root UI state and keeps transitional slice
getters while remaining single-holder call sites finish moving onto
`withRecords` / aggregate transitions. Android retains SavedStateHandle
persistence, URI access, byte staging, native viewer launch, and upload
execution while editor, attachment-request, and summary state cross shared
boundaries.

The buildable `shared-ui:records` module consumes `RecordsFeatureStateHolder`
directly for list-filter tab selection and owns its selectable layout and semantics.
Its search bar also reads aggregate query, request state, and result presence.
Its search completion status owns visual layout, status semantics, and per-event
announcement deduplication while hosts format localized result copy and bridge the
platform announcement API.
Shared records UI also owns reusable empty/error presentation. Hosts retain
localized resources, icons, and mutation callbacks.
The shared records content surface consumes this aggregate directly to compose
list filter/search controls, pull-to-refresh, initial loading and failure states,
and list/calendar body selection. Hosts provide localized and platform-specific
presentation slots.
The shared record list consumes `RecordsFeatureStateHolder` directly for published
search-result selection, filter-specific empty states, On This Day visibility,
active/deleted row branching, and pagination action state. Hosts fill localized
card/row adapter slots and route callbacks.
The shared active record row consumes `Memo` plus host-localized labels and owns
card layout, blank-content fallback, favorite/archive status, long-click semantics,
and mutation progress. Hosts retain date formatting, localized copy, icons, and
mutation routing.
The shared swipe action pane owns favorite/archive action layout, record-state
labels/icons, revealed-side visibility, and hidden-action semantics. Hosts provide
localized labels, icons, enablement, and mutation routing.
The shared quick-action sheet owns record excerpts, state-dependent supporting
copy, bottom-sheet layout, destructive styling, and two-step delete confirmation.
Hosts provide a localized date description, action strings, icons, and routing.
The shared swipe row composes these pieces and owns drag bounds, settle thresholds,
mutation resets, reveal closing, and action orchestration. Hosts only assemble
localized resources/icons and route feature mutations.
The shared recently deleted record row reads `RecordsFeatureStateHolder.mutation`
directly for per-record busy state and owns restore/purge presentation plus the
two-step permanent-delete confirmation. Hosts provide localized labels, icons,
formatted deletion timestamps, and mutation routing.
The shared calendar coverage notice reads `RecordsFeatureStateHolder.pagination`
and `CalendarMemoCoverage` directly for partial-month and loading presentation;
hosts provide localized record-count copy and the load-more callback.
The shared calendar empty-selection notice reads the same coverage result to
choose definitive-empty or possibly-incomplete host copy.
The shared calendar header owns month navigation presentation while hosts retain
locale-aware month formatting, icons, and feature mutation callbacks.
The shared calendar grid consumes month rows and entry-date counts derived by the
records feature, owns date selection presentation and semantics, and leaves
weekday ordering plus localized day descriptions to hosts.
The shared record calendar consumes `RecordsFeatureStateHolder` directly to derive
date counts, selected records, month coverage, and empty-selection state while
owning calendar-list composition. Hosts provide locale-aware grid/header values,
localized descriptions, and record-row adapter slots.
The shared on-this-day card reuses `yearsBetween` and `excerpt` for anniversary
entries while hosts provide the current date, localized title/plural formatter,
icon, and navigation callback.
