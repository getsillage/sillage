# Architecture Guide

This document describes Sillage's stable engineering boundaries. The code sources of truth listed at the end define exact fields and behavior.

## System Boundaries

Sillage is a single-user, self-hosted monolith. One Go process serves REST, Connect, attachment downloads, and the embedded Web client. Business data is stored in SQLite, while attachment bytes are stored on the local filesystem. Android is a separate client that accesses the same instance through REST and can also store data offline on the device.

```text
Web SPA -------- REST / SSE --------┐
Android -------- REST --------------+--> Echo adapters --> service / route orchestration --> Store --> SQLite
Connect client -- Connect ----------┘                                               └--> attachments/
```

The REST and Connect adapters reuse the same domain constraints. Record validation, pagination, search, detail retrieval, and writes are centralized in `server/memo/`; REST, Connect, and sync translate their transport models into service inputs. The root `server` package coordinates automatic AI summaries after creation across features. Handwritten extensions such as attachment uploads and Ask SSE are orchestrated in routes and call the Store directly. They must still reuse the same authorization and domain constraints and must not introduce separate conflict semantics.

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
| `proto/api/v1/` | Protobuf API contract source |
| `web/` | React Web source, tests, and build configuration |
| `android/` | Kotlin/Compose client and local offline data |
| `scripts/` | Container builds, startup, and Compose |

### Web Internal Boundaries

| Path | Responsibility |
| --- | --- |
| `web/src/app/` | Application startup, route composition, provider order, and global navigation shell |
| `web/src/features/auth/` | Initialization and sign-in interface |
| `web/src/features/memos/` | Record list, detail, editing, filters, and record state |
| `web/src/features/ask/` | Ask conversations, message trees, and streaming-answer state |
| `web/src/features/settings/` | AI profiles and interface settings |
| `web/src/components/` | Presentation and interaction components shared across features |
| `web/src/i18n/` | English and Simplified Chinese interface catalogs, language persistence, and locale-aware formatting state |
| `web/src/lib/` | Low-level capabilities such as API access, authentication tokens, and dates |

`app/` composes the features. Features may depend on shared `components/` and `lib/`; Ask may call the records feature to save an answer, but the records feature must not depend on Ask. `web/src/lib/api.ts` is the single transport client. API, routing, and browser-storage contracts may change only through explicit contract changes.

### Android Internal Boundaries

| Path | Responsibility |
| --- | --- |
| `android/app/src/main/java/app/sillage/ui/` | Application shell, shared UI state, ViewModel, and attachment-cache lifecycle |
| `android/app/src/main/java/app/sillage/ui/auth/` | Usage-mode selection, service connection, initialization, and sign-in UI |
| `android/app/src/main/java/app/sillage/ui/memos/` | Record list, detail, editing, and Markdown rendering |
| `android/app/src/main/java/app/sillage/ui/ask/` | Ask conversations and streaming-answer UI |
| `android/app/src/main/java/app/sillage/ui/settings/` | AI, appearance, data, and sync settings UI |
| `android/app/src/main/java/app/sillage/ui/common/` | Presentation components shared across features |
| `android/app/src/main/java/app/sillage/ui/navigation/` | Primary navigation components |
| `android/app/src/main/java/app/sillage/data/` | REST client, sessions, local storage, and data models |
| `android/app/src/main/res/values*/` | English and Simplified Chinese interface resources |

`SillageApp` only composes the UI and hands attachments to external viewers. Feature screens depend on the root `SillageUiState`, `SillageViewModel`, and shared UI, while the state and data layers must not depend on feature screens. Manual sync, navigation history, request IDs, and online/offline modes are behavior contracts that span these directories and must be preserved.

## Core Invariants

- An instance has exactly one account; initialization rejects creation of a second account.
- `memo` is the only content unit in code, the database, Proto, and APIs; English user-facing documentation and copy use `record`; the Simplified Chinese UI uses `记录`.
- `entry_date` is the date selected by the user and must not be replaced with `created_at`.
- Body content, date, favorite state, archive state, and deletion use `version` for optimistic concurrency control.
- Deletions retain tombstones so sync clients can converge.
- AI-derived data is stored separately and does not increment a memo's `version` or `updated_at`.
- Attachment downloads require authorization and filenames must be sanitized; attachment bytes do not enter sync payloads.
- AI API keys are stored only in encrypted envelopes and must never be returned by APIs or sync.

See the [Sync API](api/sync.md) for detailed pagination, idempotency, and conflict rules. See [Product Guidance](product-guidance.md) for product scope and [Security Development Boundaries](security.md) for authentication, attachment, secret, and external-request constraints.

## Data and Generated Artifacts

The default data unit is one complete `SILLAGE_DATA` directory:

```text
sillage.db
sillage.db-wal
sillage.db-shm
assets/attachments/
.thumbnail_cache/
runtime/secrets.json
```

WAL and SHM files appear only while SQLite uses them. `.thumbnail_cache/` is a currently unused reserved directory that is created during startup; `runtime/` is not a cache. See [Data, Backup, and Recovery](../user/data.md) for backup rules.

The repository commits two types of generated artifacts:

- `proto/gen/` is generated by `buf generate` and must not be edited manually.
- `server/router/frontend/dist/` is generated by `pnpm --dir web build`, embedded in the Go binary, and must not be edited manually.

## API Boundaries

- REST v1: `/api/v1/*`.
- Connect v1: `/sillage.api.v1.<Service>/<Method>`.
- Protobuf is the Connect contract source. `proto/gen/openapi/openapi.yaml` is only a generated projection of Proto HTTP annotations, not the complete REST contract.
- See the [REST API Guide](api/README.md) for REST v1 authentication, error models, versioning rules, and handwritten extensions. The implementation sources of truth are `server/*_routes.go`.
- The Web client maintains handwritten types in `web/src/lib/api.ts`; Android maintains REST mappings in `SillageApi.kt`.

Contract changes must update Proto, generated artifacts, affected REST/Connect adapters, clients, and tests. See the [Contributing Guide](../../CONTRIBUTING.md) for the procedure.

## Sources of Truth

| Topic | Source of truth |
| --- | --- |
| Runtime configuration | `cmd/sillage/main.go`, `internal/profile/profile.go` |
| Database schema and upgrades | `store/migration/sqlite/LATEST.sql`, `store/migrator.go` |
| REST routes | `server/*_routes.go` |
| Business services | `server/memo/`, `server/auth/`, `server/api_service.go` |
| Connect / OpenAPI projection | `proto/api/v1/`, `proto/gen/openapi/openapi.yaml` |
| REST contract | `docs/development/api/README.md`, `server/*_routes.go`, REST behavior tests |
| Web theme and component styles | `web/src/styles/app.css`, `web/src/components/ui.ts` |
| Interface language catalogs | `web/src/i18n/messages.ts`, `android/app/src/main/res/values*/strings.xml` |
| Automated quality gates | `.github/workflows/ci.yml` |
| Container behavior | `scripts/Dockerfile`, `scripts/entrypoint.sh`, `scripts/compose.yaml` |
