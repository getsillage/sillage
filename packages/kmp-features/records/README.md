# Records feature

Shared record-list policy and immutable feature state derived from presentation
data. The module owns mutually exclusive list filters, deterministic ordering,
On This Day calendar selectors, excerpts, and pagination-coverage decisions.

`RecordsPaginationStateHolder` owns cursor, single-flight, loading, completion,
failure, and cancellation transitions for load-more requests.
`RecordsRefreshStateHolder` separately owns snapshot replacement status and
request identity. Both holders validate their captured source, client context,
filter, cache generation, and request identity before accepting a response;
pagination also validates cursor and refresh also validates the active
pagination request generation.

The module depends only on `kmp-core:domain`. It must not perform transport,
storage, synchronization, or platform UI work. Android consumes the policies
and holders directly, retaining temporary read accessors while writes go
through shared transitions. Search, selection, and editor state remain later
extraction slices.
