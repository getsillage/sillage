# Architecture Guide

This document describes Sillage's stable engineering boundaries. The code sources of truth listed at the end define exact fields and behavior.

## System Boundaries

Sillage is a single-user, self-hosted monolith. One Go process serves the REST API, Connect API, attachment downloads, and embedded React Web client. Business data is stored in SQLite, while attachment bytes are stored on the local filesystem. The responsive Web interface is the only client and uses the instance as its canonical data store.

Public ingress, TLS termination, DNS, tunneling, CDNs, and other edge-network services sit outside the Sillage system boundary and repository. The application exposes generic HTTP and forwarded-header behavior, but it does not ship third-party network connectors, credentials, or vendor-specific deployment configuration.

```text
React Web -- REST / SSE --> Echo adapters --> service / route orchestration --> Store --> SQLite
Connect API ---------------------┘                                               └--> attachments/
```

The REST and Connect adapters reuse the same domain constraints. Record validation, pagination, search, detail retrieval, and writes are centralized in `server/memo/`; REST and Connect translate their transport models into service inputs. The root `server` package coordinates automatic AI summaries after creation across features. Handwritten extensions such as attachment uploads and Ask SSE are orchestrated in routes and call the Store directly. They must still reuse the same authorization and domain constraints and must not introduce separate conflict semantics.

## Startup Lifecycle

1. `cmd/sillage/main.go` reads flags and environment variables.
2. `internal/profile` normalizes the listen address, data directory, SQLite DSN, and runtime directory.
3. `store/migrator.go` initializes an empty database or performs a supported compatibility upgrade.
4. `internal/secret` reads or generates runtime secrets.
5. `server.New` registers probes, REST, Connect, attachment, and Web routes.
6. After SIGINT or SIGTERM, the service stops accepting requests and closes the database.

## Module Responsibilities

| Path | Responsibility |
| --- | --- |
| `cmd/sillage/` | Process entry point, configuration binding, and lifecycle |
| `internal/profile/` | Runtime configuration and directory normalization |
| `internal/secret/` | Session secrets, AI key-encryption secrets, and envelopes |
| `server/` | HTTP/Connect adapters, cross-feature orchestration, and AI calls |
| `server/auth/` | Account authentication, sessions, and token services |
| `server/memo/` | Record validation, query pagination, and write services |
| `store/` | SQLite queries, transactions, migrations, and domain persistence |
| `contracts/proto/api/v1/` | Protobuf API contract source |
| `apps/web/` | React Web source, tests, and build configuration |
| `contracts/` | Protobuf wire definitions and generated projections |
| `scripts/` | Verification, release tooling, container builds, startup, and Compose |

### Web Internal Boundaries

| Path | Responsibility |
| --- | --- |
| `apps/web/src/app/` | Application startup, route composition, provider order, and global navigation shell |
| `apps/web/src/features/auth/` | Initialization and sign-in interface |
| `apps/web/src/features/memos/` | Record list, detail, editing, filters, and record state |
| `apps/web/src/features/ask/` | Ask conversations, message trees, and streaming-answer state |
| `apps/web/src/features/settings/` | AI profiles and interface settings |
| `apps/web/src/components/` | Presentation and interaction components shared across features |
| `apps/web/src/i18n/` | English and Simplified Chinese interface catalogs, language persistence, and locale-aware formatting state |
| `apps/web/src/lib/` | Low-level capabilities such as API access, authentication tokens, and dates |

`app/` composes the features. Features may depend on shared `components/` and `lib/`; Ask may call the records feature to save an answer, but the records feature must not depend on Ask. Auth and the application shell are part of the startup bundle; record pages, Ask, timeline, and settings are route-split and loaded on demand. `apps/web/src/lib/api.ts` is the single transport client. API, routing, browser-storage, build-metadata, and bundle-size contracts may change only through explicit contract changes.

`I18nProvider` also owns the Web Toast provider so initialization, sign-in, and
authenticated routes share one bounded notification queue. Features publish
transient operation feedback through that provider; persistent retry and
confirmation state remains owned by the feature that can resolve it.
Error feedback preempts routine success or informational feedback and is
retained ahead of routine messages when the queue reaches its bound.

## Core Invariants

- An instance has exactly one account; initialization rejects creation of a second account.
- `memo` is the only content unit in code, the database, Proto, and APIs; English user-facing documentation and copy use `record`; the Simplified Chinese UI uses `记录`.
- Record deletion has two durable states: `deletedAt` is recoverable for 30 days, while `purgedAt` marks a scrubbed tombstone that must never re-enter active or Recently Deleted views.
- `entry_date` is the date selected by the user and must not be replaced with `created_at`.
- Body content, date, favorite state, archive state, and deletion use `version` for optimistic concurrency control.
- Deletions retain tombstones so stale reads and writes cannot revive permanently deleted content.
- AI-derived data is stored separately and does not increment a memo's `version` or `updated_at`.
- Attachment downloads require authorization and filenames must be sanitized.
- AI API keys are stored only in encrypted envelopes and must never be returned by APIs.
See [Product Guidance](product-guidance.md) for product scope and [Security Development Boundaries](security.md) for authentication, attachment, secret, and external-request constraints.

## Data and Generated Artifacts

The default data unit is one complete `SILLAGE_DATA` directory:

```text
sillage.db
sillage.db-wal
sillage.db-shm
sillage.db.sillage.lock
assets/attachments/
.thumbnail_cache/
runtime/secrets.json
runtime/instance.lock
```

WAL and SHM files appear only while SQLite uses them. `.thumbnail_cache/` is a currently unused reserved directory that is created during startup; `runtime/` is not a cache. The server and offline administrative commands share operating-system advisory locks on `runtime/instance.lock` and `<database-path>.sillage.lock`, preventing two supported Sillage processes from using the same data directory or SQLite file concurrently. See [Data, Backup, and Recovery](../user/data.md) for backup and recovery rules.

The repository commits generated Proto projections in `contracts/proto/gen/`; they are produced by `buf generate` and must not be edited manually.

`server/router/frontend/dist/` is generated by `pnpm --dir apps/web build`, ignored by Git, and embedded in production Go binaries. The tracked sibling `dist_placeholder.txt` keeps Go-only builds valid; when `dist/index.html` is absent, the server exposes a fallback page instead of a partially populated file server. The HTML entry document is revalidated on every navigation, while content-hashed `/assets/` files are immutable for one year. `make check-web` enforces route-split chunks and raw/gzip size budgets so a feature cannot silently return to the startup bundle.

## API Boundaries

- REST v1: `/api/v1/*`.
- Connect v1: `/sillage.api.v1.<Service>/<Method>`.
- Protobuf is the Connect contract source. `contracts/proto/gen/openapi/openapi.yaml` is only a generated projection of Proto HTTP annotations, not the complete REST contract.
- See the [REST API Guide](api/README.md) for REST v1 authentication, error models, versioning rules, and handwritten extensions. The implementation sources of truth are `server/*_routes.go`.
- The Web client maintains handwritten types in `apps/web/src/lib/api.ts`. Legacy bulk record-transfer handlers and compatibility fields remain server-side until the separate API cleanup; the Web client does not use them.
- Public bootstrap metadata and `X-Sillage-*` response headers currently expose the server version, revision, API generation, and a legacy minimum-client compatibility value; they must never contain secrets or deployment identifiers.

Contract changes must update Proto, generated artifacts, affected REST/Connect adapters, clients, and tests. See the [Contributing Guide](../../CONTRIBUTING.md) for the procedure.

## Sources of Truth

| Topic | Source of truth |
| --- | --- |
| Runtime configuration | `cmd/sillage/main.go`, `internal/profile/profile.go` |
| Database schema and upgrades | `store/migration/sqlite/LATEST.sql`, `store/migrator.go` |
| REST routes | `server/*_routes.go` |
| Business services | `server/memo/`, `server/auth/`, `server/api_service.go` |
| Legacy bulk-transfer handlers | `server/sync_routes.go`, `server/sync_apply.go` |
| Connect / OpenAPI projection | `contracts/proto/api/v1/`, `contracts/proto/gen/openapi/openapi.yaml` |
| REST contract | `docs/development/api/README.md`, `server/*_routes.go`, REST behavior tests |
| Web theme and component styles | `apps/web/src/styles/app.css`, `apps/web/src/components/ui.ts` |
| Interface language catalogs | `apps/web/src/i18n/messages.ts` |
| Automated quality gates | `.github/workflows/ci.yml` |
| Container behavior | `scripts/Dockerfile`, `scripts/entrypoint.sh`, `scripts/compose.yaml` |
