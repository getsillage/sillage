# Records feature

Shared record-list policy and immutable feature state derived from presentation
data. The module owns mutually exclusive list filters, deterministic ordering,
On This Day calendar selectors, excerpts, and pagination-coverage decisions.

- `RecordsPaginationStateHolder` owns cursor, single-flight, loading,
  completion, failure, and cancellation transitions for load-more requests.
- `RecordsRefreshStateHolder` owns snapshot replacement status and request
  identity independently from pagination.
- `RecordsSearchStateHolder` owns query input, results, failure binding,
  completion events, and request identity.
- `RecordsSelectionStateHolder` owns the selected domain record and validates
  detail responses against source, destination, session, cache, and version
  generations.
- `RecordsSummaryStateHolder` owns AI-derived summary presentation, loading,
  request identity, and detail/editor context validation.
- `RecordsEditorStateHolder` owns editor sessions, draft and initial snapshots,
  dirty state, Markdown preview, and attachment-upload request ownership.
- `RecordsMutationStateHolder` owns the active record identities for concurrent
  mutation single-flight presentation state.
- `RecordsCollectionStateHolder` owns the visible record cache and its canonical
  mutation generation.
- `RecordsBrowseStateHolder` owns list/calendar mode, semantic filtering, and
  calendar month/day selection.
- `RecordsAttachmentOpenStateHolder` owns attachment-open request identity and
  invalidation while platform hosts stage bytes and launch native viewers.
- `RecordsFeatureStateHolder` composes the holders above and owns coordinated
  multi-holder transitions for the visible list surface (clear/reset/replace/
  append), interactive workspace teardown, detail/editor presentation
  (present/clear selected memo and begin editor drafts), and canonical memo
  application (cache mutation plus load/search/selection invalidation).
  Individual holders remain the unit of request identity; the aggregate prevents
  hosts from updating those slices out of lockstep.

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
