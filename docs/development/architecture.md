# Architecture Guide

This document describes Sillage's stable engineering boundaries. The code sources of truth listed at the end define exact fields and behavior.

## System Boundaries

Sillage is a single-user, self-hosted monolith. One Go process serves REST, Connect, attachment downloads, and the embedded React Web client. Business data is stored in SQLite, while attachment bytes are stored on the local filesystem. Native clients access the same instance through HTTP and maintain local-first state on the device. Android is currently implemented; iOS, Windows, and macOS have reserved application boundaries and will share a Kotlin Multiplatform core.

Public ingress, TLS termination, DNS, tunneling, CDNs, and other edge-network services sit outside the Sillage system boundary and repository. The application exposes generic HTTP and forwarded-header behavior, but it does not ship third-party network connectors, credentials, or vendor-specific deployment configuration.

```text
React Web -------- REST / SSE -------┐
Native KMP clients -- REST / SSE ----+--> Echo adapters --> service / route orchestration --> Store --> SQLite
Connect client ----- Connect --------┘                                               └--> attachments/
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
| `contracts/proto/api/v1/` | Protobuf API contract source |
| `apps/web/` | React Web source, tests, and build configuration |
| `apps/native/androidApp/` | Kotlin/Compose Android client and local offline data |
| `apps/native/build-logic/` | Native version catalog, shared KMP build conventions, and dependency-boundary checks |
| `apps/native/iosApp/` | Reserved iOS host, Apple adapters, native UI, and packaging boundary |
| `apps/native/desktopApp/` | Reserved Windows/macOS host, native integration, and packaging boundary |
| `apps/native/shared-ui/` | Reserved Compose Multiplatform design system and application shell |
| `packages/kmp-core/` | Shared native domain, application, data, sync, and security modules; `domain`, `application`, and `sync` are buildable for Android, desktop JVM, and Apple targets |
| `packages/kmp-features/` | Feature-scoped native state and presentation modules; `records` owns shared record query policy |
| `contracts/` | Wire definitions, projections, fixtures, and compatibility policy |
| `tests/` | Cross-application contract, conformance, integration, and E2E boundaries |
| `tooling/` | Repository code generation, CI, and release-tooling boundaries |
| `scripts/` | Container builds, startup, and Compose |

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

### Native Client Boundaries

The Web client remains React and is not a Compose target. Android, iOS,
Windows, and macOS use Kotlin Multiplatform for shared runtime code and Compose
Multiplatform as the primary UI technology. Platform-native UI is allowed for
system integration, accessibility, performance, or platform-standard behavior,
but it must consume shared feature state and use cases rather than duplicate
domain, persistence, synchronization, or protocol logic. The complete rule is
defined in [Multiplatform Client Architecture](multiplatform.md).

The current Android code is the migration source for the shared native modules:

| Path | Responsibility |
| --- | --- |
| `apps/native/androidApp/src/main/java/app/sillage/ui/` | Application shell, shared UI state, ViewModel, and attachment-cache lifecycle |
| `apps/native/androidApp/src/main/java/app/sillage/ui/auth/` | Usage-mode selection, service connection, initialization, and sign-in UI |
| `apps/native/androidApp/src/main/java/app/sillage/ui/memos/` | Record list, detail, editing, and Markdown rendering |
| `apps/native/androidApp/src/main/java/app/sillage/ui/ask/` | Ask conversations and streaming-answer UI |
| `apps/native/androidApp/src/main/java/app/sillage/ui/settings/` | AI, appearance, data, and sync settings UI |
| `apps/native/androidApp/src/main/java/app/sillage/ui/common/` | Presentation components shared across features |
| `apps/native/androidApp/src/main/java/app/sillage/ui/navigation/` | Primary navigation components |
| `apps/native/androidApp/src/main/java/app/sillage/data/` | REST client, sessions, local storage, and data models |
| `apps/native/androidApp/src/main/res/values*/` | English and Simplified Chinese interface resources |

`SillageApp` currently composes the UI and hands attachments to external viewers. Feature screens currently depend on the root `SillageUiState` and `SillageViewModel`; those containers are existing implementation facts, not the target cross-platform boundary. Their behavior contracts—manual sync, navigation history, request IDs, online/offline modes, conflict handling, and feedback delivery—must be preserved while state moves into feature-scoped shared modules. New cross-platform behavior must not further enlarge the application-wide ViewModel.

`packages/kmp-core/domain` owns the shared `Memo` entity, its active-lifecycle
policy, and the platform-neutral `MemoAI` derived metadata value. Android REST
mappings, local persistence, feature state, tests, and UI consume these types
directly; `data/Models.kt` no longer defines Android-only record or AI-detail
entities. Remaining Android-local models are migration sources for later domain,
application, or feature slices.

`packages/kmp-core/application` owns repository ports and use cases. Its first
slice exposes a platform-neutral record snapshot port and list use case;
Android's `LocalRecordsRepository` adapts `LocalDataStore` to that port. Shared
application APIs do not expose Android, SQLite, JSON, HTTP, or generated DTO
types.

`packages/kmp-features/records` owns record list filters, ordering, On This Day,
calendar aggregation, excerpts, and cursor-coverage selectors. Android storage,
feature state, ViewModel orchestration, Compose UI, and tests consume these
shared policies. Calendar grid construction remains Android presentation code;
Android's repository adapter owns REST query mapping behind the shared
application port.

`LocalDataStore` owns the offline business-data contract. Its persistence boundary is `LocalStateStore`: a SQLite WAL key/value database whose values are independently encrypted with Android Keystore AES-GCM. Operations that update records together with sync metadata use one SQLite transaction. First open performs an idempotent migration from the former `sillage.local_data` SharedPreferences store; unreadable ciphertext is retained and surfaced as corruption instead of being normalized to empty state. Bounded session and interface preferences remain in `SessionStore`.

Android transient feedback is emitted once by `SillageViewModel` and consumed
by the top-level Toast host in `SillageApp`. Feature screens do not render a
second copy of global error or notice messages; durable retry, conflict, and
confirmation state remains part of the relevant screen.
The event channel is bounded, new events replace the active Toast, warning and
error feedback stays visible longer, and language changes discard buffered
messages from the previous locale.

The records application slice also exposes a semantic online page query through
`RecordsPageRepository` and `ListRecordsPageUseCase`. Android's
`RemoteRecordsRepository` maps its scope and cursor to REST parameters and
maps the Android transport page back to the shared application result.
`RecordsSearchRepository` and `SearchRecordsUseCase` expose the corresponding
full-text search boundary through the same semantic scopes. Android's local and
remote repository adapters translate that query into storage and REST calls.
`RecordDetailRepository` and `GetRecordDetailUseCase` expose one record plus its
AI-derived metadata; the same adapters map local persistence or the REST detail
response to shared domain values.
`RecordWriteRepository` and `SaveRecordUseCase` expose creation and update from
a platform-neutral draft command. Android's local and remote adapters own the
storage or REST write and return the canonical shared `Memo`.
`RecordLifecycleRepository` and `MutateRecordLifecycleUseCase` expose favorite,
archive, recoverable deletion, restoration, and permanent deletion commands.
The same adapters execute the mutation and return its canonical shared `Memo`.
`RecordSummaryGenerator` and `RecordSummaryStore` separate AI generation from
local persistence. Android validates the captured record version and client
context before invoking the store; local profile resolution, AI clients,
encrypted persistence, and REST execution remain adapter responsibilities.

The records feature now also owns the immutable
`RecordsPaginationStateHolder`, the first extracted records feature-state
slice. It validates source, client context, filter, cache generation, cursor,
and request identity before accepting a late page response. Android's root UI
state retains transitional read accessors, while pagination writes use the
shared holder's explicit begin, complete, fail, and cancel transitions.

The shared `RecordsRefreshStateHolder` now owns refresh status and request
identity. It rejects responses after source, client context, filter, cache, or
pagination generation changes, and a newer refresh supersedes an older one.
The shared `RecordsSearchStateHolder` owns normalized query state, results,
failure binding, completion events, and request identity. Android retains
debounce timing and local/remote source orchestration, while every state
transition and late-response check is shared.

The shared `RecordsSelectionStateHolder` owns the selected domain record and
detail request identity. It validates source, client session, navigation
destination, editor generation, cache generation, and record version before an
Android detail response may update state. AI-derived summary presentation state
is owned by `RecordsSummaryStateHolder`, including request identity and
detail/editor context validation. Android retains AI execution orchestration and
localized feedback. `RecordsEditorStateHolder` owns editor session identity,
draft snapshots, dirty state, preview state, and attachment-upload ownership;
Android retains draft persistence, URI access, and attachment execution. The
underlying `MemoAI` value already belongs to the shared domain.

`RecordsMutationStateHolder` owns the active record identities exposed to list
and editor presentation while lifecycle mutations run. Android retains keyed
coroutine gates and localized mutation feedback.

`RecordsCollectionStateHolder` owns the visible record cache and canonical
mutation generation. Pagination and refresh replace snapshots without inventing
a mutation, while canonical create/update/lifecycle responses advance the
generation used by late-response validation.

`RecordsBrowseStateHolder` owns list/calendar mode, semantic filtering, and
calendar month/day selection. `MemoViewMode` is a shared feature value; Android
retains platform date arithmetic and refresh scheduling.

`packages/kmp-core/sync` owns the shared pending mutation, applied result,
version-conflict, and push-summary models. Android REST/JSON mapping,
transactional outbox persistence, attachment staging, and the transactional
conflict-storage adapter remain platform-side migration sources for later sync
ports and state-machine slices.
`PushPendingMemosUseCase` composes `MemoSyncOutbox` and `MemoSyncGateway` ports:
it skips empty pushes, sends one pending batch, and acknowledges only applied
results through the transactional outbox. `MemoSyncConflictStateHolder` and
`ResolveMemoSyncConflictUseCase` own pending conflict identity and explicit
resolution commands; platform hosts retain confirmation UI and implement the
transactional conflict repository adapter.

## Core Invariants

- An instance has exactly one account; initialization rejects creation of a second account.
- `memo` is the only content unit in code, the database, Proto, and APIs; English user-facing documentation and copy use `record`; the Simplified Chinese UI uses `记录`.
- Record deletion has two durable states: `deletedAt` is recoverable for 30 days, while `purgedAt` marks a scrubbed synchronization tombstone that must never re-enter active or Recently Deleted views.
- `entry_date` is the date selected by the user and must not be replaced with `created_at`.
- Body content, date, favorite state, archive state, and deletion use `version` for optimistic concurrency control.
- Deletions retain tombstones so sync clients can converge.
- AI-derived data is stored separately and does not increment a memo's `version` or `updated_at`.
- Attachment downloads require authorization and filenames must be sanitized; attachment bytes do not enter sync payloads.
- AI API keys are stored only in encrypted envelopes and must never be returned by APIs or sync.
- Native offline state writes that span content and sync metadata are atomic, and unreadable encrypted state must never be replaced by an empty default. Android currently enforces this invariant; new native clients must inherit it through the shared persistence boundary.

See the [Sync API](api/sync.md) for detailed pagination, idempotency, and conflict rules. See [Product Guidance](product-guidance.md) for product scope and [Security Development Boundaries](security.md) for authentication, attachment, secret, and external-request constraints.

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
- The Web client maintains handwritten types in `apps/web/src/lib/api.ts`; Android maintains REST mappings in `SillageApi.kt`.
- Public bootstrap metadata and `X-Sillage-*` response headers currently expose the server version, revision, API generation, and minimum supported Android `versionCode`; they must never contain secrets or deployment identifiers. The Android-specific minimum is transitional: cross-platform compatibility will be expressed by protocol revision and capabilities rather than adding one server field per platform.

Contract changes must update Proto, generated artifacts, affected REST/Connect adapters, clients, and tests. See the [Contributing Guide](../../CONTRIBUTING.md) for the procedure.

## Sources of Truth

| Topic | Source of truth |
| --- | --- |
| Runtime configuration | `cmd/sillage/main.go`, `internal/profile/profile.go` |
| Database schema and upgrades | `store/migration/sqlite/LATEST.sql`, `store/migrator.go` |
| REST routes | `server/*_routes.go` |
| Business services | `server/memo/`, `server/auth/`, `server/api_service.go`, `server/sync_apply.go` |
| Connect / OpenAPI projection | `contracts/proto/api/v1/`, `contracts/proto/gen/openapi/openapi.yaml` |
| REST contract | `docs/development/api/README.md`, `server/*_routes.go`, REST behavior tests |
| Web theme and component styles | `apps/web/src/styles/app.css`, `apps/web/src/components/ui.ts` |
| Interface language catalogs | `apps/web/src/i18n/messages.ts`, `apps/native/androidApp/src/main/res/values*/strings.xml` |
| Automated quality gates | `.github/workflows/ci.yml` |
| Container behavior | `scripts/Dockerfile`, `scripts/entrypoint.sh`, `scripts/compose.yaml` |
