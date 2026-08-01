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
| `apps/native/shared-ui/` | Shared Compose Multiplatform UI; `app-shell` owns presentation policy, `auth` owns authentication feature UI, `records` owns records feature UI, `settings` owns settings feature UI, and `design-system` owns semantic theme tokens plus common `MaterialTheme` |
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

`shared-ui:design-system` owns the common Compose theme plus reusable
`SillageNavigationBar` and `SillageNavigationItem` components. Android's
`MainNavigationBar` supplies translated labels, Material icons, selected
destinations, and ViewModel callbacks; it does not own a second copy of the bar
layout, insets, colors, item animation, or accessibility policy.
`SillageSettingsSectionCard` similarly owns settings section heading semantics,
spacing, shape, surface color, and border. Shared info, action, switch, and
empty-state rows own common layout, disabled/selection colors, dividers, value
selection, and Switch semantics. Android supplies localized text, icons, values,
state, callbacks, and the remaining platform-specific controls.
`SillageErrorCard` provides the shared error-container layout used by Ask and
Settings; hosts retain localized messages, action labels, icons, and retry
callbacks.
Shared heading and status semantics are exposed by the design system and covered
by common tests; Android call sites retain localized status-description assembly.
Authentication form failures use shared `SillageInlineError` presentation and
assertive error semantics; Android supplies localized messages and icons.

`packages/kmp-core/domain` owns the shared `Memo` entity, its active-lifecycle
policy, and the platform-neutral `MemoAI` derived metadata value. Android REST
mappings, local persistence, feature state, tests, and UI consume these types
directly; `data/Models.kt` no longer defines Android-only record or AI-detail
entities. Remaining Android-local models are migration sources for later domain,
application, or feature slices.

Secret-free account identity lives in `kmp-core:domain/auth`. Token-bearing
sessions and public bootstrap capability metadata live in
`kmp-core:application/auth`; Android retains HTTP parsing and secure session
storage adapters.
Instance discovery crosses `InstanceBootstrapRepository`; initialization,
sign-in, account verification, and password change cross
`AuthenticationRepository` through focused use cases. Android's remote adapter
retains REST, refresh coordination, and context-safe encrypted session storage.
Sign-out uses `SignOutRepository` and `SignOutUseCase`; a prepared operation binds
remote invalidation and conditional local clearing to one captured session, so a
late failure cannot clear credentials established by a newer sign-in.

Authentication presentation state lives in `packages/kmp-features/auth`.
`AuthFeatureStateHolder` is the feature aggregate nested on Android root state;
`AuthenticationStateHolder` owns credential drafts and password-change
validation/request identity, including client-context checks that discard late
callbacks. It contains no session token or transport type; Android supplies
localized messages and implements the application ports. Android credential-draft
updates and primary-credential clearing pass through root `withAuth` thin wrappers;
application-level loading remains outside the auth aggregate.
`shared-ui:auth` consumes `AuthFeatureStateHolder` directly for login and account
initialization forms and also owns the server-address form plus reusable
password-field, authentication-action, and mode-selection-card presentation.
Android supplies localized strings, icons, accent colors, root loading state,
navigation, protocol execution, and ViewModel callbacks. The shared scaffold owns
centered layout, heading semantics, and inline-error placement; the shared header
owns brand/language layout while Android supplies launcher resources, localized
content, language state, and the toggle callback.
The same module consumes the auth aggregate for settings password-change drafts
and request state, and owns the account settings section wrapper. Android supplies
account metadata and client-context gating.
`shared-ui:settings` consumes `SettingsFeatureStateHolder` directly for the AI
profile editor header and summary cards, including save progress, diagnostics
feedback, and profile/action presentation.
The same module owns AI profile detail editing, provider selection, model-result
chips, connection feedback, delete confirmation, lazy-list composition, and
expanded-profile selection. Android supplies localized strings, icons, and
ViewModel callbacks while retaining adapters.
The shared automatic-summary section reads preference and save lifecycle from the
same aggregate, with Android supplying only its wider application operation gate.
It also owns settings language-selection layout; Android supplies supported
language identifiers, localized labels, and preference persistence.
The shared appearance section composes theme selection and the language row while
Android maps stored preference values and callbacks.
The service/sync section owns online/offline and conditional sync-action layout;
Android retains client-context mutation, navigation, and protocol execution.
The data section owns import/export action presentation; Android retains document
launchers, serialization, and storage adapters.
The about section owns metadata-row and license-entry presentation; Android maps
build/server protocol values and loads packaged notice resources.
The shared settings list owns loading, retry error, list spacing/padding, and test
semantics while Android supplies localized values and shared section items.
`SillageSettingsContent` owns section ordering, optional account placement, and
the multi-item profile tail; Android slots map resources and adapter callbacks.
The shared settings overview card receives localized display values derived from
Android app-mode, records, appearance, and AI state without owning those adapters.

`shared-ui:records` consumes `RecordsFeatureStateHolder` directly for record-list
filter selection. Its first slice owns the four-tab selectable layout, colors, and
Tab semantics while Android supplies localized labels and the mutation callback.
The shared search bar also reads query, searching, and result-presence state from
the aggregate and owns IME submission, progress, clear, and search action layout.
Shared records empty/error presentation owns icon treatment, centered copy, and an
optional action while Android selects localized copy for each list condition.

Ask conversation/message/source-reference values and secret-free AI settings
metadata also live in `kmp-core:domain`. Android transport, persistence, feature,
and test call sites import these values directly. Cross-platform AI profile
editor drafts live in `kmp-features:settings`; platform API inputs, encrypted
storage mapping, and transport DTOs remain adapter-side types.

Ask conversation/message reads, conversation creation, and branch-head
selection cross `AskRepository` and focused use cases in
`packages/kmp-core/application`. Android local and remote adapters own SQLite and
REST translation. Remote answer delivery crosses `AskAnswerStreamer` and
`StreamAskAnswerUseCase` as ordered start, delta, and failure events. Android
retains SSE parsing, HTTP/session behavior, and device-local AI execution.
Offline generation crosses `AskAnswerGenerator`; completed turns cross the
separate `AskTurnStore`. Shared settings and records use cases supply the active
profile and record context, while Android adapters own the local model client and
persistence transaction.

`packages/kmp-features/ask` owns `AskConversationStateHolder`, which keeps the
conversation collection, current conversation, selected branch head, and loaded
messages consistent. `AskFeatureStateHolder` composes the extracted Ask holders
and owns coordinated workspace teardown, screen-entry session advancement,
blank-composition starts, conversation load transitions, variant-head
application, stream finish coordination, composer updates, source-detail opening, and active snapshot replacement. Android also composes
records/settings/ask clears through `clearClientWorkspace` for client-context
changes without enlarging a global ViewModel. Android's root
`SillageUiState` stores one `ask` aggregate value with transitional slice getters
for the former top-level Ask holders. Android routes pure Ask mutations through
`withAsk`, with thin composer and source-navigation wrappers at the root-state
boundary, while persistence and streaming stay outside the feature module.
The same module's `AskVariantStateHolder` owns branch-selection request identity;
Android supplies navigation and client context, then applies completion only when
the shared holder still owns that request.
`AskMemoSaveStateHolder` applies the same rule to answer-to-record requests and
also captures source answer content and branch-head identity. It delegates the
actual record creation to the records application boundary.
`AskSourceNavigationStateHolder` owns source-record request identity using stable
destination/history keys rather than Android navigation types. The Android host
maps those keys to `Screen` only at its presentation boundary.
`AskStreamStateHolder` owns answer-generation request identity, live stream
presentation, regeneration identity, and completion events. Android's SSE and
device-local AI adapters feed it only after shared request-ownership validation.
`AskLoadStateHolder` owns the remaining conversation/message load status and
durable retry message rather than leaving those transitions in the root ViewModel.
`AskComposerStateHolder` owns the prompt draft and retrieval options; stream
execution captures them without folding transport behavior into composer state.
`AskSessionStateHolder` owns the monotonic feature-screen generation used by every
Ask request holder for navigation-safe callback invalidation.

`AIAutoSummaryRepository` and `SetAIAutoSummaryUseCase` own the first settings
application boundary. Android adapters implement encrypted local persistence and
REST mutation; the root ViewModel no longer selects those mechanisms directly.
`AIProfilesRepository` and `SaveAIProfilesUseCase` similarly own profile-save
intent. Their command carries write-only key input and parsed numeric values;
their result is canonical secret-free domain metadata. Android local and remote
adapters retain encrypted persistence, REST mapping, and local cache
reconciliation.
`AISettingsRepository` and `LoadAISettingsUseCase` own consistent profile and
automatic-summary reads. The application snapshot can carry an optional
device-local key beside canonical profile metadata for offline execution;
remote adapters return no secret, and the domain entity remains secret-free.
AI provider diagnostics use separate `AIProfileConnectionTester` and
`AIProfileModelCatalog` application capabilities. Android's device-local adapter
implements testing only, while its REST adapter implements testing and model
discovery from the same platform-neutral configuration command.
`packages/kmp-features/settings` owns `AIAutoSummaryStateHolder`, including
optimistic mutation, rollback, and request ownership. Android supplies client
context and user-facing feedback around the shared transition result.
The module also owns `AIProfileDraft` and its editor-only identity, raw input,
validation, and safe API-key reconciliation policy. Platform adapters must omit
editor identity from persistence and must not expose secret input as domain
metadata.
`AIProfilesMutationStateHolder` composes the draft collection and owns optimistic
profile-save state, rollback, and request ownership across mode or client-context
changes.
`AISettingsLoadStateHolder` owns load progress, retry failure, and request
ownership independently from profile saves. The host explicitly cancels the
opposite lifecycle when either begins, preventing stale loads from replacing a
newer optimistic editor snapshot.
`AIProfileDiagnosticsStateHolder` owns provider-test and model-catalog request
state and results. Completion requires the same stable editor key, complete
draft snapshot, mode, and client generation; adapter callbacks cannot attach
results to a removed or subsequently edited profile.
`SettingsFeatureStateHolder` composes those holders and owns coordinated
workspace teardown, editable profile-draft replacement, and loaded/imported
editable-settings snapshot application, diagnostic-result clearing, and host
feedback recording. Request identity remains owned by the diagnostics holder.
Android's root `SillageUiState` stores one `settings` aggregate field with
transitional slice getters; coordinated writes move onto the aggregate.

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

`shared-ui:app-shell` turns root-state feedback changes into one-shot events and
owns event IDs, duplicate suppression, error precedence, notice severity, and
language binding. Android supplies localized messages through `SillageViewModel`
and consumes events in the top-level Toast host in `SillageApp`. Feature screens
do not render a
second copy of global error or notice messages; durable retry, conflict, and
confirmation state remains part of the relevant screen.
Android's event channel is bounded, new events replace the active Toast, warning
and error feedback stays visible longer, and language changes discard buffered
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
Android retains draft persistence, URI access, and attachment execution. Shared
editor-action policy combines host destination/operation context with editor,
selection, and mutation state to decide unsaved-draft and Back-blocking behavior;
Android maps the result to localized feedback. The underlying `MemoAI` value
already belongs to the shared domain.

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

`RecordsAttachmentOpenStateHolder` owns attachment-open request identity and
invalidation so late staging or viewer events cannot cross navigation context.
Authenticated download crosses generic `AttachmentDownloadRepository` and
`DownloadAttachmentUseCase`, streaming to a host-provided destination. Android
retains cache/content-URI staging, MIME mapping, and native viewer launch.
Remote upload crosses `AttachmentUploadRepository` through
`UploadAttachmentUseCase`; the shared command/result contain only content and
canonical metadata, while multipart encoding and response parsing remain in the
Android adapter.

`RecordsFeatureStateHolder` composes the extracted records holders and owns the
cross-holder list-surface, browse filter/view-mode, interactive-workspace,
detail/editor presentation, source-memo absorption, and canonical-memo
transitions. Android's root UI state now owns one `records` aggregate value with
transitional slice getters for the former top-level holder fields. Coordinated
list-surface, browse mode/filter, workspace teardown, selected-memo
presentation, detail-request acceptance, editor session starts/returns,
draft/Markdown updates, attachment-upload transitions, attachment-open request
allocation/completion/invalidation, record-mutation begin/finish presentation,
source-record absorption, search input/clear, and canonical memo writes go through
the aggregate. Full-data import also applies restored records view preference
through the aggregate without inventing an interactive filter change. Request
identity and late-response checks stay on the individual holders. Android request
helpers update that aggregate through `withRecords`.

`packages/kmp-core/sync` owns the shared pending mutation, applied result,
version-conflict, and push-summary models. Android REST/JSON mapping,
transactional outbox persistence, attachment staging, and the transactional
conflict-storage adapter remain platform-side migration sources for later sync
ports and state-machine slices.
`PushPendingMemosUseCase` composes `MemoSyncOutbox` and `MemoSyncGateway` ports:
it skips empty pushes, sends one pending batch, and acknowledges only applied
results through the transactional outbox. `kmp-features:sync` owns pending conflict presentation through
`SyncFeatureStateHolder` and `MemoSyncConflictStateHolder`; core
`ResolveMemoSyncConflictUseCase` owns explicit resolution commands. Android
stores one `sync` aggregate with a transitional conflict-state getter. Platform
root-state writes for push results, conflict dismissal, and conflict-list
replacement pass through `withSync` thin wrappers. Platform
hosts retain confirmation UI and implement the transactional conflict repository
adapter.

Full synchronization pull uses shared `SyncSnapshot`, `SyncSnapshotGateway`, and
`SyncSnapshotRepository` contracts. The snapshot excludes backup format metadata
and client presentation preferences. `PullSyncUseCase` fetches one completed
snapshot and hands it to an atomic merge adapter; an unavailable AI-settings
section preserves existing local settings.

Android `RemoteSyncSnapshotGateway` maps REST pagination into that snapshot and
`LocalSyncSnapshotRepository` delegates one transactional merge. The Android
`SillageExportData` v1 codec remains a separate backup/local-storage DTO; adapter
mapping preserves client theme/view preferences and device-held AI secrets.
`RunSyncPushUseCase` executes the platform attachment-preparation port before
reading the outbox. `RunTwoWaySyncUseCase` then owns the required push-before-pull
sequence while the host presents localized results.

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
