# Records feature

Shared record-list policy and derived presentation data. The module currently
owns mutually exclusive list filters, deterministic ordering, On This Day and
calendar selectors, excerpts, and pagination-coverage state.

The module depends only on `kmp-core:domain`. It must not perform transport,
storage, synchronization, or platform UI work. Android consumes these policies
directly while state-holder and editor extraction continue in later slices.
