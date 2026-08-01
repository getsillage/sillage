# Settings feature

Settings profile editing, persistence request state, validation feedback, and
provider capability presentation.

The first buildable slice is `AIAutoSummaryStateHolder`. It owns the independently
saved automatic-summary preference, optimistic mutation, rollback, request
identity, and client-context validation. Storage and REST remain platform
adapters behind `kmp-core:application`.
