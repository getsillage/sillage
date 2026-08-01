# Records feature

Shared record-list policy and feature state derived from presentation data. The
module owns mutually exclusive list filters, deterministic ordering, On This
Day calendar selectors, excerpts, pagination-coverage decisions, and the
immutable `RecordsPaginationStateHolder`.

The pagination holder is the first increment extracted from Android's records
state. It owns cursor, single-flight, loading, completion, failure, and
cancellation transitions. Every response is checked against its captured
source, client context, filter, cache generation, cursor, and request identity
before it may change state.

The module depends only on `kmp-core:domain`. It must not perform transport,
storage, synchronization, or platform UI work. Android consumes the policies
and holder directly, retaining temporary read accessors while all pagination
writes go through the shared holder. Refresh, search, selection, and editor
state remain later extraction slices.
