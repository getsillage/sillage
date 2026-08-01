# Multiplatform Client Architecture

This document defines the stable client-technology and dependency boundaries
for Web, Android, iOS, Windows, and macOS. Product behavior remains governed by
[Product Guidance](product-guidance.md); wire behavior remains governed by the
[REST API Guide](api/README.md) and authored definitions under `contracts/`.

## Platform Strategy

| Platform | Primary UI | Shared runtime | Current state |
| --- | --- | --- | --- |
| Web | React and TypeScript | Web feature and infrastructure modules | Implemented |
| Android | Compose Multiplatform | Kotlin Multiplatform | Existing Compose app; shared-module extraction pending |
| iOS | Compose Multiplatform | Kotlin Multiplatform | Application and native-UI boundaries reserved |
| Windows | Compose Multiplatform | Kotlin Multiplatform | Desktop packaging and integration boundaries reserved |
| macOS | Compose Multiplatform | Kotlin Multiplatform | Desktop packaging and integration boundaries reserved |

React components are not shared with Compose. The clients share public wire
contracts, language-neutral conformance fixtures, terminology, behavior rules,
and semantic design tokens. Native clients additionally share Kotlin domain,
application, persistence, synchronization, feature state, and reusable Compose
UI modules.

## Native Dependency Direction

```text
Android / iOS / Desktop application hosts
                  |
        shared Compose UI and features
                  |
          application use cases
                  |
             domain model
                  ^
                  |
 network / database / security platform adapters
```

Dependencies point inward. Domain code does not depend on Compose, HTTP,
SQLite, generated DTOs, or operating-system APIs. Generated transport types are
mapped at the network boundary. Platform applications compose modules and
provide adapters; shared modules never depend on an application host.

## Compose and Native UI Boundary

Compose Multiplatform is the default for native feature screens, navigation,
design-system components, and application structure. A platform-native surface
may use SwiftUI, UIKit, AppKit, WinUI, or Android framework UI when at least one
of these conditions applies:

- the surface is an operating-system integration with no adequate Compose API;
- the native control provides materially better accessibility or input behavior;
- platform-standard window, menu, file, share, authentication, or lifecycle
  behavior would otherwise be degraded;
- measured performance cannot be met by the shared implementation.

A native surface consumes the same feature state, events, and use cases as its
Compose equivalent. It does not implement its own synchronization algorithm,
database rules, API mapping, conflict policy, or domain validation. Duplicating
an entire feature implementation requires an ADR with ownership and conformance
tests.

## Shared Native Modules

`packages/kmp-core/` owns domain, application, network, database,
synchronization, and security foundations. Its buildable `domain`, `application`,
and `sync` modules produce Android, desktop JVM, and Apple targets from
common source. `application` declares inward-facing use cases and repository
ports; platform adapters implement those ports. `packages/kmp-features/` owns
feature-scoped state for authentication, records, Ask, settings, and manual
synchronization. `apps/native/shared-ui/` owns reusable Compose presentation and
the shared application shell.

The buildable `kmp-features:records` module is the first shared feature slice.
It depends only on `kmp-core:domain` and owns list/filter/calendar query policy;
Android remains its first host and adapter provider. Feature state holders move
into the module incrementally without moving transport or persistence details
with them.

The buildable `kmp-features:ask` module owns conversation selection, branch-head
identity, and loaded message snapshots through `AskConversationStateHolder`.
Its transitions reject cross-conversation messages and late snapshots for a
previous selection. Android keeps transitional read accessors; persistence, SSE,
and device-local AI execution remain platform adapters.
`AskVariantStateHolder` also owns branch-selection single-flight identity and
validates screen session, conversation, source mode, and client generation before
accepting completion.
`AskMemoSaveStateHolder` similarly owns answer-to-record request identity and
rejects completion after answer content, branch head, navigation session, source,
or client context changes. Record persistence still crosses the records
application port.
`AskSourceNavigationStateHolder` captures platform-neutral destination/history
keys and rejects source-record responses after navigation, conversation, source,
or client context changes. Platform hosts map the stable keys to their own
navigation types.
`AskStreamStateHolder` owns generation single-flight identity, transient live
messages, regeneration identity, and completion events. SSE parsing and
device-local model execution remain platform adapters that may update the holder
only while its captured conversation and client context match.
`AskLoadStateHolder` owns conversation/message loading and retry presentation
through explicit begin, complete, fail, and cancel transitions.
`AskComposerStateHolder` owns the question draft and retrieval scope/source
options independently from stream execution.
`AskSessionStateHolder` owns the monotonic screen generation captured by Ask
requests so navigation invalidates late callbacks consistently across hosts.

The settings application slice begins with `AIAutoSummaryRepository` and
`SetAIAutoSummaryUseCase`. Platform adapters persist the independently saved
preference through encrypted local storage or REST without exposing either
implementation to shared callers.
The buildable `kmp-features:settings` module starts with
`AIAutoSummaryStateHolder`, which owns optimistic preference mutation, rollback,
request identity, and client-context validation.

Every shared module applies the repository `sillage.kmp-library` convention.
The convention owns target and compiler configuration; module files own only
their dependencies and optional capability plugins. `checkShared` discovers
all convention modules, runs their desktop host tests, and enforces the native
dependency direction as an executable architecture rule. Platform hosts do not
apply this convention because they own platform lifecycle and packaging.

There is no global feature ViewModel. Each feature owns its state holder and
exposes an explicit contract. Application-wide state is limited to the active
workspace, authenticated session, locale, theme, navigation root, and a summary
of synchronization status.

The first extracted records holder governs load-more state. Its immutable
transitions capture and validate source, client context, filter, cache
generation, cursor, and request identity so late responses cannot cross query
boundaries. The holder is available to every native host; Android retains only
transitional accessors while the rest of records state is migrated.

The records application boundary has separate ports for a consistent local
snapshot and a server-backed page. Shared callers use semantic scope and cursor
values; platform adapters own storage transactions, HTTP parameter mapping, and
transport DTO conversion.

Records refresh is the second extracted state slice. Its shared holder owns
loading/failure status and request identity independently from pagination, while
validating the same query context before replacing the visible snapshot.

Records search also crosses an application port: shared callers provide text
and semantic scope, while local and remote platform adapters own storage or REST
query mapping.

Single-record detail retrieval crosses `RecordDetailRepository` and
`GetRecordDetailUseCase`. Its result combines the shared `Memo` and `MemoAI`
domain values; local and remote Android adapters own persistence and REST
response mapping.

Ask conversation and message reads, conversation creation, and branch-head
selection cross the shared `AskRepository` through focused application use
cases. Android local and remote adapters translate storage and REST calls.
Streaming answer generation and device-local AI execution stay adapter-side
until those asynchronous execution contracts are extracted independently.

Record creation and update cross `RecordWriteRepository` and
`SaveRecordUseCase`. Shared commands carry only domain records and draft values;
Android adapters own local persistence or REST writes.

Favorite, archive, recoverable deletion, restoration, and permanent deletion
cross `RecordLifecycleRepository` and `MutateRecordLifecycleUseCase`. Shared
commands preserve mutation intent while Android adapters own local persistence
or REST calls.

AI summary generation crosses `RecordSummaryGenerator`; local summary
persistence crosses `RecordSummaryStore` only after the host validates the
captured record version and client context. Android adapters own local profile
resolution, AI clients, encrypted persistence, and REST execution.

Records search is the third extracted state slice. The shared holder owns query,
result, failure, completion-event, and request-identity transitions; hosts own
debounce scheduling and choose the active local or remote application adapter.

Records selection/detail is the fourth extracted state slice. The shared holder
owns the selected `Memo` and detail request validation. `MemoAI` is now a shared
domain value.

Records summary is the fifth extracted state slice. Its shared holder owns the
selected summary, loading state, request identity, and detail/editor context
validation. Android retains coroutine scheduling, user-facing messages, and the
local or remote AI execution adapters.

Records editor is the sixth extracted state slice. Its shared holder owns editor
session identity, draft and initial snapshots, dirty state, Markdown preview,
and attachment-upload request ownership. Android retains SavedStateHandle draft
restoration, content-URI access, and local or remote attachment execution.

Records mutation is the seventh extracted state slice. Its shared holder owns
the active record identities used by concurrent mutation presentation state;
Android retains coroutine gates, source selection, and localized feedback.

Records collection is the eighth extracted state slice. Its shared holder owns
the visible record cache and canonical mutation generation. Snapshot and page
replacement preserve that generation; applying a canonical mutation advances it
and re-applies the active semantic filter.

Records browsing is the ninth extracted state slice. Its shared holder owns
list/calendar mode, semantic filtering, and calendar month/day selection.
Android retains platform date arithmetic and refresh scheduling.

Attachment opening is the tenth extracted records state slice.
`RecordsAttachmentOpenStateHolder` owns request identity, late-result ownership,
and invalidation. Android retains authenticated download, content-URI and cache
staging, MIME resolution, and native viewer launch.

The first buildable `kmp-core:sync` slice owns pending mutation, applied result,
conflict, and push-summary models. Android retains current REST/JSON mapping,
transactional outbox persistence, attachment staging, and the transactional
conflict-storage adapter until later sync ports and state-machine slices are
extracted.
`PushPendingMemosUseCase` already composes shared `MemoSyncOutbox` and
`MemoSyncGateway` ports so empty-push handling and applied-result acknowledgement
are platform-independent.
`kmp-features:sync` owns pending conflict presentation identity through
`MemoSyncConflictStateHolder`; core `ResolveMemoSyncConflictUseCase` owns the
explicit keep-local/take-server command workflow.
Platform hosts retain confirmation UI and implement the transactional repository
port without duplicating conflict policy.

Full pull uses a distinct shared `SyncSnapshot`; it is not a backup-file DTO.
`PullSyncUseCase` composes transport and atomic-merge ports. Snapshot sections
contain syncable domain values only, and an unavailable AI-settings section means
"preserve local settings" rather than "replace with empty settings".
Android implements the gateway and atomic repository ports while retaining the
versioned `SillageExportData` v1 codec solely for local storage and file backup.
Shared `RunSyncPushUseCase` and `RunTwoWaySyncUseCase` enforce attachment
preparation before push and push-before-pull ordering. Platform hosts implement
attachment staging and present the resulting status.

## Platform Hosts

- `apps/native/androidApp/` is the Android application and current migration
  source.
- `apps/native/iosApp/` owns the Xcode host, Apple lifecycle, signing, Keychain,
  file and share integration, and optional SwiftUI/UIKit adapters.
- `apps/native/desktopApp/` owns the shared desktop executable plus Windows and
  macOS packaging, signing, secure-storage, window, menu, shortcut, and optional
  native-UI adapters.

Platform hosts may depend on shared modules. They must not depend directly on
another platform host.

## Local Data and Synchronization

The native clients converge on one shared local-first model: local writes and
outbox mutations commit atomically; pulled resources and cursor advancement
commit atomically; conflicts are durable and explicitly resolved; attachment
bytes use a staged upload path outside structured sync payloads; and data is
partitioned by Sillage workspace or instance.

The current Android implementation remains the behavioral reference while it
is extracted. Its application-wide ViewModel, handwritten transport client, and
Android-specific persistence classes are migration sources, not target module
boundaries. New cross-platform behavior belongs in the shared modules rather
than expanding those containers.

Extraction proceeds in vertical slices that remain consumed by Android. The
first slice moves the record entity and its active-lifecycle policy into
`packages/kmp-core/domain`; Android REST, local persistence, feature state, and
Compose UI all use that shared type. Platform or transport models must not
reintroduce another record entity.

Ask conversation/message/source-reference values and secret-free AI settings
metadata follow the same rule. Platform hosts may keep UI drafts, API inputs,
codecs, and secure-key persistence, but must consume the shared domain values.

## Verification

Shared domain and synchronization modules require common unit tests and
language-neutral fixtures under `contracts/fixtures/` and `tests/conformance/`.
Each platform retains packaging, signing, accessibility, lifecycle, and device
tests. Platform-native UI replacements must pass the same feature contract and
acceptance scenarios as the shared Compose surface.
