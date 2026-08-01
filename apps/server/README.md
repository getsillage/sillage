# Server migration target

This directory reserves the target boundary for the Go modular monolith.

The existing `cmd/`, `internal/`, `server/`, and `store/` packages remain the
runtime sources of truth during the initial repository migration. Moving them
here requires a separate phase that first defines domain modules and enforces
their dependency direction; a mechanical path move alone would not improve the
architecture.
