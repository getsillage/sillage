# ADR: Recoverable record deletion and scrubbed tombstones

- Status: Accepted
- Date: 2026-07-30

## Context

Sillage previously retained the complete body and related derived data in deletion tombstones indefinitely. That allowed long-offline clients to converge, but users had neither an ordinary recovery workflow nor a permanent-delete operation. Treating a deleted row as either immediately absent or permanently content-bearing would force a choice between offline correctness and a credible privacy lifecycle. Offline clients can also collapse several transitions before the server sees them, such as delete followed by restore or delete followed by permanent deletion.

Attachments and AI-derived data cross the record boundary. A permanent-delete design therefore has to define what happens to summaries, Ask citations, shared attachment bytes, local pending attachments, old clients, backups, and concurrent synchronization rather than only clearing one database column.

## Decision

- Ordinary deletion sets `deletedAt` and moves the record to Recently Deleted. The body remains recoverable for 30 days.
- Restore clears `deletedAt` and increments the record version.
- Explicit purge, or maintenance after the 30-day window, sets `purgedAt` and scrubs the body, user-selected date, favorite/archive state, memo AI, generated summaries that cite the record, and Ask answers whose retained source references cite it.
- The purged row remains as a minimal structural tombstone so clients returning after a long offline period cannot resurrect the record.
- An attachment is retained while another non-purged record still references it. When the last live reference is purged, its metadata is tombstoned and maintenance removes server bytes; Android removes unshared pending local bytes immediately.
- Sync supports `create`, `update`, `delete`, `restore`, and `purge`. Lifecycle actions express the desired final state: delete and restore are no-ops when that state already exists at the matching base version, and purge may atomically delete then scrub an active server record when the client collapsed intermediate offline transitions.
- Clients that only understand `deletedAt` remain safe because a purged tombstone also remains deleted. New clients use `purgedAt` to discard cached derived data and never display scrubbed placeholder fields as user content.

## Consequences

Users can recover accidental deletion during a bounded window and can intentionally remove current-database content without sacrificing long-offline convergence. Permanent deletion is irreversible through the product; recovery requires a backup made before purge, so operators must align backup retention with their privacy commitments.

Purged rows and attachment metadata consume small amounts of durable storage because synchronization tombstones are not aged out. Removing them later requires a separate full-client-reset or epoch protocol. The lifecycle is a cross-module contract: schema, maintenance, REST, Connect, sync, Web, Android, documentation, and tests must change together.

The server maintenance schedule means automatic scrub happens on the first maintenance run after the record reaches 30 days, normally at startup or within the next six-hour interval. Shared references deliberately override eager byte deletion; a file cannot be removed while a non-purged record still depends on it.
