# Constitution

**Audience:** maintainers, reviewers, coding agents
**Owner:** project maintainers
**Enforcement:** human review for product intent; CI and repository policy for technical red lines
**Last reviewed:** 2026-07-30

This is the L0 product and engineering constitution. Detailed procedures live in [Contributing](../../CONTRIBUTING.md), [Governance](governance.md), and the domain guides linked below. Rules that can be enforced by tests or CI must not rely on this document alone.

## Product identity

Sillage is a **self-hosted, single-user** space for private records, history review, and AI answers grounded in the user's own notes.

One-line description:

> Self-hosted, single-user space for private records, history review, and AI answers grounded in your own notes.

## Non-negotiable boundaries

1. **Single account per instance.** Initialization rejects a second account.
2. **`memo` is the only content unit** in backend, database, Proto, and API code. English user-facing copy uses **record**; Simplified Chinese UI uses **记录**.
3. **Writing comes first.** Do not turn the product into a multi-user collab tool, social network, public profile host, task manager, knowledge-base wiki, or complex file drive.
4. **No official hosted service** and no vendor-locked edge/network deployment paths in this repository. Public ingress, TLS, DNS, tunnels, and CDNs are operator-owned.
5. **AI is optional and source-grounded for personal claims.** General questions may use model knowledge without citations; claims about the user's life or history require cited records. Do not diagnose users or present speculation as fact.
6. **AI services for edge platforms** may only be reached as operator-configured compatible endpoints. No named provider presets, adapters, or defaults for those platforms.
7. **No built-in backup UI, server backup API, or backup CLI.** Backup remains an operator data-directory procedure.
8. **Do not commit secrets, live databases, attachments, keystores, or unsigned release binaries** into the repository.

## Engineering red lines

| Area | Rule | Primary enforcement |
| --- | --- | --- |
| API contracts | Proto is the Connect contract source; generated `contracts/proto/gen` is committed and must match `buf generate` | CI `check-proto` |
| Schema | New DBs use `LATEST.sql`; upgrades go through idempotent migrator steps | Go migration tests |
| Concurrency | Memo writes use `version`; deletions use tombstones; no silent overwrite | Tests + review |
| Secrets | AI API keys only as encrypted envelopes; never returned by API or sync | Security tests + review |
| Web embed | `server/router/frontend/dist/` is generated and gitignored | CI embed policy |
| Releases | User-visible artifacts come from the Release workflow only | Release policy |

## Documentation law

- One canonical document per topic; other places summarize and link.
- Intent and boundaries live in docs; exact behavior lives in code and tests.
- On conflict, **code wins**, and the same change must fix the doc.
- Significant, hard-to-reverse cross-module decisions get an ADR under `docs/development/decisions/`.

## Normative references

| Topic | Document |
| --- | --- |
| Governance registry, dual track, automation | [Governance](governance.md) |
| Product semantics and AI behavior | [Product Guidance](product-guidance.md) |
| Modules and sources of truth | [Architecture](architecture.md) |
| Auth, attachments, secrets, external calls | [Security Development Boundaries](security.md) |
| Contributor environment and commands | [Contributing](../../CONTRIBUTING.md) |
| Agent session constraints | [CLAUDE.md](../../CLAUDE.md) |
