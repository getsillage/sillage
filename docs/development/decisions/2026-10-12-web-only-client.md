# Web as the sole application client

## Context

Sillage is a self-hosted space for one account's records. Its maintained user
interface, runtime contracts, quality gates, and distribution need one clear
application boundary. The responsive browser interface already provides the
record, attachment, Ask, settings, and account workflows.

## Decision

- Maintain `apps/web/` as the sole application client and keep the Go monolith as its backend.
- Use standard record REST/Connect operations, server-assigned record IDs, optimistic write versions, and recoverable deletion. Preserve attachment-upload retry identifiers.
- Keep canonical records and AI execution on the server; browser drafts and interface preferences remain browser-local.
- Build and publish the embedded Web application in the server container for `linux/amd64` and `linux/arm64`.
- Keep build dependencies, code generation, notices, CI, release validation, and documentation scoped to this maintained product.
- Remove unused application and module scaffolds instead of preserving planned development boundaries.

## Consequences

Development requires Go, Node.js, pnpm, and Buf, with Docker and browser test
dependencies for the complete verification suite. Web behavior is verified in
Chromium, Firefox, and WebKit; release evidence covers the server image,
upgrade, restore, and rollback.

Contract retirement must be documented in release compatibility notes and
reserve removed Proto field identifiers. The compatibility baseline exception
is limited to the retired bulk service, bootstrap field 5, and create field 1;
the exact reviewed historical fingerprints and compiled field reservations
are required, and all maintained contracts keep Buf `FILE` checks. Compatible database upgrades preserve
records, attachments, account data, sessions, and deletion tombstones. A
rollback restores the complete pre-upgrade data directory.
