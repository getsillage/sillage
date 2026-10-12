# ADR: Recoverable record deletion and scrubbed tombstones

- Status: Accepted
- Date: 2026-07-30

## Context

Retaining the complete body and related derived data in deleted rows indefinitely
provides neither an ordinary recovery workflow nor a credible permanent-delete
operation. Record deletion must define the effect on summaries, Ask citations,
shared attachment bytes, backups, and concurrent edits.

## Decision

- Ordinary deletion sets `deletedAt` and moves the record to Recently Deleted. The body remains recoverable for 30 days.
- Restore clears `deletedAt` and increments the record version.
- Explicit purge, or maintenance after the 30-day window, sets `purgedAt` and scrubs the body, user-selected date, favorite/archive state, memo AI, generated summaries that cite the record, and Ask answers whose retained source references cite it.
- The purged row remains as a minimal structural tombstone. Reads and writes must never return scrubbed placeholder fields as user content or revive purged records.
- An attachment is retained while another non-purged record still references it. When the last live reference is purged, its metadata is tombstoned and maintenance removes server bytes.
- Lifecycle writes use version checks; stale requests fail rather than silently replacing newer state.

## Consequences

Users can recover accidental deletion during a bounded window and intentionally
remove content from the current database. Permanent deletion is irreversible
through the product; recovery requires a backup made before purge, so operators
must align backup retention with their privacy commitments.

Purged rows and attachment metadata consume small amounts of durable storage.
The lifecycle is a cross-module contract: schema, maintenance, REST, Connect,
Web, documentation, and tests must change together.

The server maintenance schedule means automatic scrub happens on the first
maintenance run after the record reaches 30 days, normally at startup or within
the next six-hour interval. Shared references deliberately override eager byte
deletion; a file cannot be removed while a non-purged record still depends on it.
