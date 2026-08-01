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

All asynchronous holders validate captured source, client context, filter,
cache generation, and request identity before accepting a response. Pagination
also validates cursor; refresh validates the active pagination generation; and
search binds published results to the normalized query.

The module depends only on `kmp-core:domain`. It must not perform transport,
storage, synchronization, or platform UI work. Android consumes the policies
and holders directly, retaining temporary read accessors while writes go
through shared transitions. Android retains SavedStateHandle persistence, URI
access, and upload execution while editor and summary state cross shared
boundaries.
