# Multiplatform Client Architecture

This document defines the stable client-technology and dependency boundaries
for Web, Android, iOS, Windows, and macOS. Product behavior remains governed by
[Product Guidance](product-guidance.md); wire behavior remains governed by the
[REST API Guide](api/README.md) and authored definitions under `contracts/`.

## Platform Strategy

| Platform | Primary UI | Shared runtime | Current state |
| --- | --- | --- | --- |
| Web | React and TypeScript | Web feature and infrastructure modules | Implemented |
| Android | Compose Multiplatform | Kotlin Multiplatform | Implemented; shared-module extraction active |
| iOS | Compose Multiplatform in a SwiftUI/UIKit host | Kotlin Multiplatform | Device-local records prototype; public bootstrap and memory-only authentication implemented; sync and Keychain persistence pending |
| Windows | Compose Multiplatform | Kotlin Multiplatform | Device-local records prototype; public bootstrap and memory-only authentication implemented; sync and Credential Manager persistence pending |
| macOS | Compose Multiplatform | Kotlin Multiplatform | Device-local records prototype; DMG, public bootstrap, and memory-only authentication implemented; sync and Keychain persistence pending |

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
synchronization, and security foundations. Its buildable `domain`,
`application`, `local-data`, `network`, and `sync` modules produce Android,
desktop JVM, and Apple targets from common source. `application` declares inward-facing use
cases and repository ports; platform adapters implement those ports.
`local-data` owns the versioned JSON client-snapshot codec, optimistic record
version checks, lifecycle rules, device-local preference persistence, and a
separate validated backup-v1 envelope compatible with Android's record subset.
Platform hosts supply atomic string storage, timestamps, record IDs, and native
document pickers without forking codec, restore, or lifecycle behavior.

`network` owns public server bootstrap URL validation, response bounds, HTTP
status handling, JSON mapping, and conversion into application values behind a
small host HTTP port. It also owns base-URL-scoped initialization, sign-in,
bearer retry after one refresh, password change, and context-bound sign-out.
Desktop and Apple networking APIs remain platform adapters. The public request
carries no records, credentials, cookies, or authorization headers. Native
authentication keeps access tokens and refresh cookies in a generation-checked
memory session; OS-backed persistence remains a separate security adapter.

Record create and update commands share the server's draft constraints in
`application`: non-empty content, at most 1 MiB after UTF-8 encoding, and a
valid `YYYY-MM-DD` entry date. `local-data` repeats the check at its direct write
boundary so future adapters cannot bypass it. Snapshot and backup decoding keep
structurally valid historical records readable instead of treating newer draft
rules as a destructive migration.

`packages/kmp-features/` owns
feature-scoped state for authentication, records, Ask, settings, and manual
synchronization. `apps/native/shared-ui/` owns reusable Compose presentation and
the shared application shell.

The buildable `shared-ui:application` composition root provides a responsive
records list/detail/editor workflow, record search and lifecycle actions,
appearance settings, bilingual copy, and unsaved-draft protection. Desktop and
iOS hosts supply only platform adapters and lifecycle entry points. This first
host slice is intentionally device-local: online authentication, remote sync,
Ask, attachments, and credential storage must arrive through existing shared
application ports rather than host-only implementations.

Secret-free `auth.Account` is a shared domain value. Token-bearing
`AuthSession` and public server `BootstrapInfo` are application values rather
than domain entities; HTTP parsing and secure session persistence remain
platform adapters.
Public capability discovery crosses `InstanceBootstrapRepository`.
Initialization, sign-in, current-account verification, and password change cross
`AuthenticationRepository` through focused use cases. Android retains REST,
refresh coordination, and context-safe encrypted session persistence.
Sign-out crosses a separate `SignOutRepository`: its prepared operation captures
the current session before asynchronous work, and shared application policy owns
offline clearing, remote-failure fallback, cancellation, and stale-session
rejection without exposing token-bearing platform snapshots.

Canonical theme, language, and app-mode tokens plus URL/mode normalization live in
`kmp-core:application` (`ClientPreferenceValues`). Android `SessionStore` remains the
secure/local preference adapter and reuses those shared normalizers.

The first buildable `shared-ui:app-shell` slice owns application-wide theme and
interface-language state plus their platform-neutral transitions. Android
hydrates it from `SessionStore`, persists accepted changes through that adapter,
and remains responsible for applying the system theme and locale. The module
also owns native root destination identity, navigation-history updates, fallback
back navigation, and return-to-Records policy; hosts retain system Back dispatch
and platform navigation effects. One-shot global feedback sequencing, error
precedence, duplicate suppression, notice severity, and language binding also
belong to the app shell; hosts retain localized message production and native
feedback rendering.

`AppClientContextStateHolder` aggregates root destination identity, navigation
history, application mode, workspace generation, and the server-settings return
destination. It owns their pure selection, workspace reset, sign-out reset,
server return, and navigation transitions. Hosts retain preference persistence,
network and cancellation effects, system Back dispatch, and platform navigation
effects.
`AppWorkspaceStateHolder` aggregates the records, settings, and Ask feature
holders. It owns coordinated clearing and offline-workspace entry, preventing a
host from publishing a partially reset interactive workspace. Hosts still own
repository reads and writes, cancellation, and platform-specific side effects.
Android theme/language orchestration, Compose consumers, localized feedback, and
tests read the nested appearance state directly without root appearance accessors.
They also consume nested client-context state directly without root compatibility
accessors.

The buildable `shared-ui:design-system` module is the first shared Compose
surface. It owns Sillage's semantic light/dark color schemes, typography, shapes,
and common `MaterialTheme`. Android consumes the shared theme and keeps only its
`WindowCompat` status/navigation-bar icon adaptation; Apple and desktop hosts can
consume the same common theme without inheriting Android system APIs.
`SillageNavigationBar` owns the primary-navigation container, divider, system
insets, content height, and selectable-group semantics. `SillageNavigationItem`
owns primary-item layout, semantic selected/disabled colors, indicator animation,
and Tab role; hosts supply localized labels, icons, destination selection, and
callbacks.
`SillageSettingsSectionCard` owns settings section heading semantics, spacing,
shape, semantic surface colors, and border treatment. Shared info, action,
switch, and empty-state rows own common layout, disabled/selection colors,
dividers, value selection, and Switch semantics; hosts supply localized text,
icons, values, state, callbacks, and platform-specific controls.
`SillageErrorCard` owns reusable error-container layout and semantic colors for
Ask and Settings loading failures; hosts supply localized messages, action
labels, icons, and callbacks.
The design system also owns heading and concise status-announcement semantics;
hosts provide localized status descriptions while common tests preserve the
accessibility contract.
`SillageInlineError` owns compact error layout, semantic colors, and assertive
live-region/error semantics; hosts supply localized messages and icons.

The buildable `shared-ui:auth` module is the first shared feature UI surface. Its
login and account-initialization forms consume `AuthFeatureStateHolder` directly
and the module also owns server-address entry, password visibility, IME
submission, loading/action presentation, form layout, and mode-selection-card
presentation. Its scaffold owns centered scroll/IME layout, heading semantics,
and inline-error placement; its header owns brand/language layout. Hosts supply
localized strings, icons, accent colors, root loading state, navigation, protocol
callbacks, brand resources, and language state; platform adapters retain resource
lookup and language persistence.
The shared auth module also owns settings account/password-change content and
its section wrapper, consuming the auth aggregate directly; hosts supply account
metadata and client-context mutation gates.

The buildable `shared-ui:settings` module owns the AI profile editor header and
summary cards. It consumes `SettingsFeatureStateHolder` directly for save
progress, editable profile metadata, diagnostics feedback, selection colors,
fallbacks, and action-enabled policy.
Its detail editor also consumes draft fields, model results, and diagnostic
request state while owning provider selection and deletion confirmation. Shared
lazy-list orchestration owns header/empty/profile composition and expanded-profile
selection. Hosts supply localized strings, icons, and callbacks; persistence,
protocol clients, native dialogs, and file pickers remain platform adapters.
The automatic-summary section also consumes preference and save lifecycle from the
aggregate, combining them with a host-provided wider operation gate.
The module also owns settings language-selection layout and option presentation;
hosts provide supported language identifiers, localized labels, and persistence.
Its appearance section composes theme selection with that row while hosts map
stored preference values and persistence callbacks.
Its service/sync section owns mode-selection and conditional server/sync action
composition while hosts retain navigation, client-context mutation, and protocols.
Its data section owns import/export action presentation while hosts retain native
file pickers, serialization, and storage adapters.
Its about section owns metadata-row and license-entry presentation while hosts
provide build/protocol values and native notice resources.
Its settings list owns loading, retry error, lazy spacing/padding, and list test
semantics while hosts emit shared sections into the content slot.
`SillageSettingsContent` owns section order, optional account placement, and the
multi-item profile tail while host slots map resources and adapter callbacks.
Its overview card accepts localized mode, record-count, appearance, and AI values,
leaving cross-feature state mapping in each host adapter.

The buildable `shared-ui:records` module begins with record-list filter tabs. It
consumes `RecordsFeatureStateHolder` directly for the selected filter and owns the
selectable layout, semantic Tab roles, colors, and sizing. Hosts supply localized
labels and the filter mutation callback.
Its search bar consumes query, request-progress, and published-result presence from
the same aggregate while hosts supply localized content, icons, and callbacks.
Its search completion status owns visual layout, status semantics, and per-event
announcement deduplication while hosts format localized result copy and bridge the
platform announcement API.
Its reusable empty/error state owns centered copy, icon treatment, and optional
action layout while hosts choose localized condition-specific content.
Its records content surface consumes `RecordsFeatureStateHolder` directly and
owns list filter/search composition, pull-to-refresh, initial loading and failure
selection, plus list/calendar body switching. Hosts retain Scaffold/navigation,
localized resources, accessibility announcement bridges, and locale-aware
calendar adapters.
Its screen chrome consumes the aggregate view/filter/refresh state to own
records/calendar title selection, heading layout, refresh gates, deleted-filter
FAB visibility, and action layout. Hosts supply localized account/server subtitle
formatting and retain the Scaffold shell.
Its record list consumes `RecordsFeatureStateHolder` directly to select published
search results, filter-specific empty states, On This Day visibility, active/deleted
row branching, and pagination action state. It owns lazy-list composition while
hosts fill localized card/row adapter slots and route callbacks.
Its record summary section consumes aggregate summary state and owns
summary-card layout,
loading/action selection, body fallback, and provider/model/source/token metadata
presentation. Hosts retain localized plural formatting and any platform codec
needed to interpret source identifiers.
Its record detail card owns date/status layout, lifecycle status joining, content
separation, and blank-record fallback. Hosts provide localized labels and retain
platform Markdown/attachment handling in the content slot; the shared status line
is reusable outside the detail card.
Its metadata block derives prior-revision count and owns divider/label layout while
hosts format localized created/updated timestamps and revision plurals.
Its record detail content shell consumes aggregate selection state and owns
missing-record fallback, section order,
lazy-list spacing, and shared content width while hosts retain Scaffold/TopAppBar placement
and fill localized record, summary, and metadata slots.
Its record detail actions consume aggregate selection/mutation state and own
edit/more enablement, lifecycle-aware favorite/archive choices, mutation-driven
menu reset, and delete confirmation; hosts supply the host-operation gate,
localized resources, icons, and feature callbacks.
Its record editor actions consume aggregate selection/editor/mutation state plus
`RecordsEditorActionContext` and own save-progress semantics, attachment-vs-save
busy copy precedence, lifecycle-aware menu choices, and delete confirmation while
hosts map destination/global-operation context and provide localized mode-specific
supporting copy, icons, and callbacks.
Its editor screen chrome consumes the same aggregate/context and owns new-vs-edit
title selection, heading semantics, back-button layout, and action gating. Hosts
retain `TopAppBar`, platform Back dispatch, localized strings, and icons.
Its editor close request consumes aggregate editor state plus
`RecordsEditorActionContext` and owns dirty-draft selection and discard-confirmation
state/dialog while hosts retain platform Back integration and supply localized
copy plus the close callback.
Its editor content shell consumes aggregate selection/editor state plus
`RecordsEditorActionContext` and owns lazy-list section ordering, shared content
width, lifecycle status, draft-date/action enablement, attachment action, and
existing-record summary visibility. Hosts retain platform date/file launchers and
fill online-mode capability, Markdown-editor, and summary slots.
Its active record row consumes `Memo` plus host-localized labels and owns card
layout, blank-content fallback, favorite/archive status, long-click semantics, and
mutation progress. Hosts retain date formatting, localized copy, icons, and feature
routing.
Its swipe action pane owns favorite/archive action layout, record-state labels and
icons, revealed-side visibility, and hidden-action semantics. Hosts provide
localized labels, icons, enablement, and mutation callbacks.
Its quick-action sheet owns record excerpts, state-dependent supporting copy,
bottom-sheet layout, destructive styling, and two-step delete confirmation. Hosts
provide a localized date description, action strings, icons, and callbacks.
Its swipe row composes these pieces and owns drag bounds, settle thresholds,
mutation resets, reveal closing, and action orchestration. Hosts only assemble
localized resources/icons and route feature mutations.
Its recently deleted record row also consumes the records aggregate and `Memo`,
owns mutation-aware restore and two-step permanent-delete presentation, and leaves
localized labels, icons, formatted deletion timestamps, and callbacks to hosts.
Its calendar coverage notice consumes shared coverage and pagination state, owns
partial-coverage selection and loading presentation, and leaves localized
record-count copy plus pagination routing to hosts.
Its calendar empty-selection notice consumes the same coverage result and owns the
choice between definitive-empty and possibly-incomplete host strings.
Its calendar header owns navigation layout and semantics while hosts supply
calendar arithmetic, locale-formatted month labels, icons, and callbacks.
Its calendar grid owns date layout, counts, today/selection styling, and semantics;
hosts provide locale-ordered weekday labels and localized day descriptions.
Its record calendar consumes `RecordsFeatureStateHolder` directly to derive date
counts, selected records, month coverage, and empty-selection state while owning
calendar-list composition. Hosts provide locale-aware grid/header values,
localized descriptions, and record-row adapter slots.
Its on-this-day card consumes record entries plus the host date, owns anniversary
calculation, excerpts, dividers, and selection, and leaves localized plural
formatting, the icon, and navigation to hosts.

The buildable `shared-ui:ask` module consumes `AskFeatureStateHolder` directly.
Its first slice owns retrieval-range/source selection, selected chip state,
bottom-sheet layout, and heading semantics. Hosts supply localized strings and
route feature callbacks while retaining streaming, Markdown, navigation, and
platform integrations.
Its conversation sheet also owns aggregate-derived refresh/selection gates,
empty/current row presentation, title fallback, and select-then-dismiss flow;
hosts provide localized copy and callbacks.
Its composer owns context-label selection, trimmed character count, IME Send
gating, question layout, and send/stop action switching. Hosts provide localized
formatting, icons, callbacks, and may reuse the shared context label elsewhere.
Its empty/live cards own prompt guidance, user/answer card layout, waiting
fallback, colors, and message accessibility descriptions while hosts provide
localized copy, icons, and speaker-description formatting.
Its message actions own neighboring-variant selection, regenerate/save
visibility and request gates, saving progress, and polite position semantics;
hosts provide localized strings, icons, and callbacks.
Its source references own expansion state, the five-row display limit, row
layout, and action gating; hosts provide localized count/date formatting,
expand/collapse icons, and navigation callbacks.
Its message card owns stored/streaming/regenerating content selection,
user/assistant bubble presentation, semantics, and source/action slot placement.
Hosts keep final Markdown and protected-attachment handling in the platform
content slot.
Its message list consumes the aggregate plus shared active path to own initial
loading, error/empty/message/live item order, lazy-list layout, and per-message
action gates. Hosts fill localized content slots and retain Scaffold/top-bar,
navigation, and platform announcement bridges.
Its auto-follow behavior owns drag suspension, near-bottom reactivation,
new-turn following, streamed-answer growth scrolling, and rendered-item counts;
hosts retain platform completion-announcement bridges.
Its top-bar title/actions own context presentation, saving progress semantics,
Material action layout, and aggregate request gates; hosts provide localized
resources/icons and retain the top-app-bar/Scaffold shell.

The buildable `kmp-features:auth` module owns native authentication form drafts
and password-change presentation state. `AuthFeatureStateHolder` is the feature
aggregate composed by Android root state; `AuthenticationStateHolder` performs
platform-neutral validation, allocates single-flight request identities, captures
the active app/client context, rejects stale completions, and clears password
material after a successful change. Hosts provide localized validation messages
and execute the application use case; tokens and secure session storage never
enter feature state. Android routes credential-draft updates and primary-credential
clearing through root `withAuth` thin wrappers; application-level loading remains
outside the auth feature.
Android authentication orchestration, ViewModel paths, Compose screens, and tests
consume the aggregate directly rather than host-root authentication accessors.

The buildable `kmp-features:records` module is the first shared feature slice.
It depends only on `kmp-core:domain` and owns list/filter/calendar query policy;
Android remains its first host and adapter provider. Feature state holders move
into the module incrementally without moving transport or persistence details
with them.

The buildable `kmp-features:ask` module owns conversation selection, branch-head
identity, and loaded message snapshots through `AskConversationStateHolder`.
Platform ViewModels, shared/native UI, and tests consume the holder through the Ask
aggregate directly rather than host-root conversation accessors. Its transitions
reject cross-conversation messages and late snapshots for a
previous selection. The module also owns active-path entries, variant grouping,
branch-leaf selection, and latest-assistant lookup so hosts do not rebuild
message-tree policy in platform adapters. `AskFeatureStateHolder` composes the
extracted Ask holders
and owns coordinated workspace teardown, screen-entry session advancement,
blank-composition starts, conversation load transitions, variant-head
application, stream finish coordination, composer updates, source-detail opening, and active snapshot replacement. Android's root
`SillageUiState` stores one `workspace` aggregate without transitional feature
getters. Thin root transition wrappers read Ask state through `workspace.ask`.
Persistence, SSE, and device-local AI
execution remain platform adapters.
`AskVariantStateHolder` also owns branch-selection single-flight identity and
validates screen session, conversation, source mode, and client generation before
accepting completion. Platform navigation gates and request orchestration read it
through the Ask aggregate directly rather than host-root variant accessors.
`AskMemoSaveStateHolder` similarly owns answer-to-record request identity and
rejects completion after answer content, branch head, navigation session, source,
or client context changes. Platform request gates and orchestration read it through
the Ask aggregate directly rather than host-root memo-save accessors. Record
persistence still crosses the records application port.
`AskSourceNavigationStateHolder` captures platform-neutral destination/history
keys and rejects source-record responses after navigation, conversation, source,
or client-context changes. Platform request orchestration reads the holder through
the Ask aggregate directly, while hosts map stable keys to their own navigation
types.
`AskStreamStateHolder` owns generation single-flight identity, transient live
messages, regeneration identity, and completion events. SSE parsing and
device-local model execution remain platform adapters and may update the holder
only while its captured conversation and client context match. Platform stream
job cleanup and completion-announcement bridges read the holder through the Ask
aggregate directly rather than host-root compatibility getters.
`AskLoadStateHolder` owns conversation/message loading and retry presentation
through explicit begin, complete, fail, and cancel transitions. Platform request
gates read it through the Ask aggregate directly rather than host-root load
accessors.
`AskComposerStateHolder` owns the question draft and retrieval scope/source
options independently from stream execution. Platform ViewModels and tests consume
it through the Ask aggregate directly rather than host-root composer accessors.
`AskSessionStateHolder` owns the monotonic screen generation captured by Ask
requests so navigation invalidates late callbacks consistently across hosts.
Platform request orchestration reads it through the Ask aggregate directly rather
than host-root session accessors.

The settings application slice begins with `AIAutoSummaryRepository` and
`SetAIAutoSummaryUseCase`. Platform adapters persist the independently saved
preference through encrypted local storage or REST without exposing either
implementation to shared callers.
AI profile saves cross `AIProfilesRepository` and `SaveAIProfilesUseCase` using
an explicit platform-neutral write command. The application result contains
canonical secret-free `AIProfile` metadata; Android adapters map commands to
encrypted local storage or REST inputs, and API-key material never enters the
domain result.
Settings reads cross `AISettingsRepository` and `LoadAISettingsUseCase` as one
consistent snapshot. A snapshot may pair domain profile metadata with a
device-local decrypted key for offline execution, but that key remains an
application configuration value and is never added to the domain profile or a
remote response.
Connection tests and model discovery cross the focused
`AIProfileConnectionTester` and `AIProfileModelCatalog` capabilities. Local
device execution implements only testing; remote REST implements both, without
forcing unsupported operations into either adapter.
The buildable `kmp-features:settings` module starts with
`AIAutoSummaryStateHolder`, which owns optimistic preference mutation, rollback,
request identity, and client-context validation.
It also owns `AIProfileDraft`, including raw editor input, validation helpers,
and safe reconciliation of secret-free server responses with locally held API
key input. Canonical secret-free profile metadata remains in `kmp-core:domain`;
platform adapters alone serialize encrypted keys or map save/test commands.
`AIProfilesMutationStateHolder` owns the editable profile collection plus
optimistic save, rollback, request identity, and client-context validation.
`AISettingsLoadStateHolder` separately owns settings-load progress, durable retry
failure, and stale-response rejection. Load and profile-mutation starts cancel
the opposite lifecycle instead of sharing an ambiguous request counter.
`AIProfileDiagnosticsStateHolder` owns connection-test and model-list request
identity, busy presentation, and per-profile results. It binds every callback to
the full draft snapshot, stable editor key, mode, and client context, so edited
or removed profiles cannot receive late diagnostic results.
`SettingsFeatureStateHolder` composes those holders and owns coordinated
workspace teardown, editable profile-draft replacement, and loaded/imported
editable-settings snapshot application. Diagnostic-result clearing and host
feedback recording also go through the settings aggregate; request identity remains
owned by the diagnostics holder. Android state orchestration, Compose screens, and
tests consume the aggregate through `workspace.settings`, rather than former
host-root settings accessors. Offline workspace entry hydrates the complete
workspace aggregate atomically, so Android
settings screen entry reuses that snapshot; online entry still loads through the
shared application port and request-identity holder.

Every shared module applies the repository `sillage.kmp-library` convention.
The convention owns target and compiler configuration; module files own only
their dependencies and optional capability plugins. `checkShared` discovers
all convention modules, runs their desktop host tests, and enforces the native
dependency direction as an executable architecture rule. Platform hosts do not
apply this convention because they own platform lifecycle and packaging.

There is no global feature ViewModel. Each feature owns its state holder and
exposes an explicit contract. Application-wide state is limited to the active
workspace aggregate, authenticated session, locale, theme, navigation root, and
a summary of synchronization status.

The first extracted records holder governs load-more state. Its immutable
transitions capture and validate source, client context, filter, cache
generation, cursor, and request identity so late responses cannot cross query
boundaries. Platform pagination orchestration and tests consume the holder through
the records aggregate directly rather than host-root pagination accessors. Local
and remote adapters still execute the page query.

The records application boundary has separate ports for a consistent local
snapshot and a server-backed page. Shared callers use semantic scope and cursor
values; platform adapters own storage transactions, HTTP parameter mapping, and
transport DTO conversion.

Records refresh is the second extracted state slice. Its shared holder owns
loading/failure status and request identity independently from pagination, while
validating the same query context before replacing the visible snapshot. Platform
refresh orchestration and tests consume it through the records aggregate directly
rather than host-root refresh accessors.

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
at the implementation boundary. Remote streaming crosses `AskAnswerStreamer`
and `StreamAskAnswerUseCase` through ordered start, delta, and failure events;
Android retains SSE parsing, HTTP/session behavior, and device-local execution.
Offline answer generation crosses `AskAnswerGenerator`, and completed local
turns cross `AskTurnStore`. The shared settings snapshot selects the enabled
active profile and the records use case supplies context; Android adapters retain
the model client and local persistence transaction.

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
Android orchestration, list reset/announcement gates, and tests consume the holder
through the records aggregate directly rather than host-root search accessors.
Shared records surface selectors also choose list-load and search failure
visibility directly from the aggregate, without host-root-state policy.

Records selection/detail is the fourth extracted state slice. The shared holder
owns the selected `Memo` and detail request validation. `MemoAI` is now a shared
domain value. Platform detail orchestration, editor/navigation gates, and tests
consume the holder through the records aggregate directly rather than host-root
selection accessors.

Records summary is the fifth extracted state slice. Its shared holder owns the
selected summary, loading state, request identity, and detail/editor context
validation. Platform orchestration state, save paths, and tests consume it through
the records aggregate directly rather than host-root summary accessors. Android
retains coroutine scheduling, user-facing messages, and the local or remote AI
execution adapters.

Records editor is the sixth extracted state slice. Its shared holder owns editor
session identity, draft and initial snapshots, dirty state, Markdown preview,
and attachment-upload request ownership. Android retains SavedStateHandle draft
restoration and content-URI access. Android orchestration, draft persistence,
upload gates, and tests consume the holder through the records aggregate directly
rather than host-root editor accessors. Remote uploads cross
`AttachmentUploadRepository` and `UploadAttachmentUseCase`; Android maps the
shared byte command to multipart HTTP while uploaded metadata remains
platform-neutral.

Shared editor-action policy combines destination availability, host-operation
state, attachment upload, and selected-record mutation to decide unsaved-draft
and Back-blocking behavior; hosts provide localized feedback.

Records mutation is the seventh extracted state slice. Its shared holder owns
the active record identities used by concurrent mutation presentation state;
platform operation gates and tests consume it through the records aggregate
directly rather than host-root mutation accessors. Android retains coroutine
gates, source selection, and localized feedback.

Records collection is the eighth extracted state slice. Its shared holder owns
the visible record cache and canonical mutation generation. Snapshot and page
replacement preserve that generation; applying a canonical mutation advances it
and re-applies the active semantic filter. Platform host counts, late-response
checks, and tests consume the holder through the records aggregate directly rather
than host-root collection accessors.

Records browsing is the ninth extracted state slice. Its shared holder owns
list/calendar mode, semantic filtering, and calendar month/day selection.
Android retains platform date arithmetic and refresh scheduling.

Android browse contexts, navigation/calendar presentation, import/export reads,
and tests consume the holder through the records aggregate directly rather than
host-root browse accessors.

Attachment opening is the tenth extracted records state slice.
`RecordsAttachmentOpenStateHolder` owns request identity, late-result ownership,
and invalidation. Platform request gates, Compose surfaces, and tests read it
through the records aggregate directly rather than host-root attachment-open
accessors. Authenticated download crosses generic
`AttachmentDownloadRepository` and `DownloadAttachmentUseCase`, which accept a
host destination without materializing response bytes in shared state. Android
retains content-URI/cache staging, MIME resolution, and native viewer launch.

`RecordsFeatureStateHolder` is the records feature aggregate. It composes the
extracted holders and owns cross-holder transitions for visible-list reset/
replace, pre-refresh loading marks, browse filter/view-mode application,
interactive workspace teardown, detail/editor presentation, source-memo
absorption, and canonical memo application so list loads, search ownership, and
selection stay consistent. Android consumes the aggregate through
`workspace.records` and exposes no root compatibility getter. Coordinated host
writes such as cache mutation,
visible-list clear/replace/append, pagination cancel/stop-loading-more, browse
mode/filter/calendar changes, workspace teardown, selected-memo presentation,
detail-request acceptance, editor session starts/returns, draft/Markdown updates,
attachment-upload transitions, attachment-open request allocation/completion/
invalidation, record-mutation begin/finish presentation, source-record absorption,
search input/clear, persisted view-mode restoration after full-data import, and
filter reset go through the aggregate; individual holders still own request
identity. Android request helpers write through `withRecords` rather than assigning
nested holder fields directly.

The first buildable `kmp-core:sync` slice owns pending mutation, applied result,
conflict, and push-summary models. Android retains current REST/JSON mapping,
transactional outbox persistence, attachment staging, and the transactional
conflict-storage adapter until later sync ports and state-machine slices are
extracted.
`PushPendingMemosUseCase` already composes shared `MemoSyncOutbox` and
`MemoSyncGateway` ports so empty-push handling and applied-result acknowledgement
are platform-independent.
`kmp-features:sync` owns pending conflict presentation through
`SyncFeatureStateHolder` / `MemoSyncConflictStateHolder`; core
`ResolveMemoSyncConflictUseCase` owns the explicit keep-local/take-server
command workflow. Android stores one `sync` aggregate on root UI state.
Resolution callbacks look up the current item through
`SyncFeatureStateHolder.findConflict` rather than host-root compatibility getters.
Push-result application, conflict dismissal, and conflict-list replacement pass
through root `withSync` thin wrappers.
The buildable `shared-ui:sync` module consumes the aggregate directly and owns
first-conflict selection, preview fallback and limits, dialog layout, and
resource-ID action routing. Platform hosts retain localized strings,
asynchronous resolution, and the transactional repository port without
duplicating conflict policy.

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
- `apps/native/iosApp/` owns the static KMP framework, SwiftUI/UIKit lifecycle
  host, atomic Application Support snapshot adapter, one-time `NSUserDefaults`
  migration, Foundation time/identity values, public bootstrap and memory-only
  authentication `NSURLSession` transport, system JSON backup document pickers,
  a branded AppIcon catalog, and Xcode integration. Keychain session
  persistence, signing, and App Store packaging remain future host work.
- `apps/native/desktopApp/` owns the shared desktop executable, atomic local
  snapshot file adapter, data-directory instance lock, platform time and identity
  values, data-folder action, native backup save/open dialogs, public bootstrap
  and memory-only authentication JDK HTTP transport, native menu and guarded
  close integration, branded
  ICNS/ICO assets, and Windows/macOS packaging. Shared local-data code
  owns the distinct portable backup envelope, validation, and replace-after-
  validation policy.
  Matching CI hosts build and verify the DMG and MSI. Signing, notarization,
  Credential Manager/Keychain session persistence, the updater, and deeper OS
  integration remain future release work.

Desktop and iOS packages produced by local build tasks are engineering
verification artifacts. They are not attached by the official release workflow
until code signing, macOS notarization, updates, and platform release-candidate
coverage are implemented.

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

Run `make check-desktop` for shared native common tests, desktop host tests,
and desktop JVM production compilation. Run `make check-desktop-package` on
macOS or Windows to build and validate the matching DMG or MSI. Main CI runs
both host-native package checks and retains the unsigned artifacts temporarily
for engineering inspection. Packaging checks do not replace signing,
notarization, accessibility, or installer lifecycle testing.

Run `make check-ios` on macOS to link the static device and simulator
frameworks, typecheck the Swift bridge, and build the unsigned simulator host.
The complete target requires Xcode with an iOS Simulator platform installed;
framework linking alone does not prove app lifecycle, accessibility, signing,
device behavior, or App Store readiness.
