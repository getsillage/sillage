# Sillage Android Guide

The native Android client supports Android 8.0 and later. It can connect to a self-hosted instance or save records offline on the device.

Download released APKs from [GitHub Releases](https://github.com/getsillage/sillage/releases). Before installing, verify the version, SHA-256 checksum, and signature information in the Release. The server and Android client should use the same release version or a version combination explicitly documented as compatible in the release notes.

On connection, the app reads the server/API version and minimum supported Android `versionCode` from the public bootstrap response. If the installed build is too old, online connection and synchronization are blocked to prevent incompatible writes. The blocking prompt links only to the project's GitHub Releases page because Sillage does not provide Play Store or in-app automatic updates; Offline mode remains available. Settings → About shows the app version code plus the server, revision, API, and minimum-version metadata used for support diagnostics.

## Connecting to an Instance

Release APKs require HTTPS. Debug builds permit HTTP so local development can reach an emulator or a trusted LAN host:

- From a debug build in an emulator to the host: `http://10.0.2.2:5231`
- From a debug build on a physical device to a trusted LAN host: for example, `http://192.168.1.10:5231`
- For a public instance: use the HTTPS address of its operator-managed external entry point

Both online and offline modes support records, calendar, search, favorites, archives, Recently Deleted, AI settings, summaries, and Ask. Deletion is recoverable for 30 days; restoring clears the deletion state, while permanent deletion scrubs the record and related derived data. Online mode additionally supports initialization and sign-in, attachment uploads, and authenticated downloads. Local data can be imported and exported, and synchronization can be run manually as a pull, push, or two-way sync.

The interface supports English and Simplified Chinese. Simplified Chinese is used by default; change the language in Settings under Appearance. The choice is stored on this device and does not translate or modify existing records, summaries, or Ask content.

The record editor supports Markdown editing and preview. The preview supports core CommonMark syntax, strikethrough, tables, task lists, and single line breaks. Raw HTML is not executed; image syntax is displayed as an attachment or external link that can be opened safely.

The app provides neither automatic background sync nor push notifications; sync is always started by the user. Offline attachment bytes are stored on the device and uploaded through `POST /api/v1/attachments` during the next online push or two-way sync (attachment bytes never enter the sync payload). After upload, memo markdown is rewritten to the authenticated server URL so later downloads use `/file/attachments/...`. An Android export is not a substitute for a complete backup of the server's data directory.

Offline records, Ask history, local AI configuration, attachment metadata, and sync state are stored as independently encrypted values in a SQLite WAL database. The encryption key is non-exportable and held by Android Keystore. On the first open after upgrading, the app migrates the former encrypted or plaintext `sillage.local_data` SharedPreferences entries into the database and removes the legacy copies. If the Keystore key or ciphertext is unavailable, the app fails closed and preserves the unreadable payload instead of treating it as an empty library and overwriting it. Android automatic backup remains disabled; use the explicit JSON export for device-to-device portability and protect that plaintext export as sensitive data.

"Pull" reads all syncable data from the server and merges it into the device. "Push" uploads pending local records (after flushing offline attachment uploads). "Two-way sync" pushes first and then performs a full pull. When a version conflict occurs, the app keeps the local pending change and opens a conflict dialog that shows the local content and the server resource. You can keep the device version (adopt the server version as the next `baseVersion` and resubmit later), use the server version (drop the local pending change), or dismiss and decide later. Do not keep retrying to force an overwrite without resolving the conflict.

## Architecture

Android is a Compose Multiplatform host. The host owns Android lifecycle, encrypted
storage, networking, attachment handling, and other platform integrations. Record
listing, search, detail retrieval, editor saves, lifecycle mutations, and summary generation cross repository ports and use cases in
`kmp-core:application`;
local and remote adapters implement the same application contracts. Ask conversation
and message reads, conversation creation, and branch-head selection also cross
the shared application boundary. Streaming answer generation and device-local AI
execution use Android adapters; remote delivery crosses the shared
`AskAnswerStreamer` contract, while SSE parsing and HTTP/session behavior remain
inside the remote adapter. Offline generation and turn persistence cross
`AskAnswerGenerator` and `AskTurnStore`; Android retains the device model client
and local storage adapters. Reusable record
collection, browsing, refresh, search, selection, detail-request validation, summary, editor, attachment-open request, and mutation state
lives in `kmp-features:records`. Android UI code may compose those shared feature states, but
must not duplicate domain, storage, synchronization, or protocol rules.
Ask conversation selection, branch-head identity, and loaded message snapshots
live in `kmp-features:ask`; Android retains transitional read accessors while
streaming and remaining request lifecycle state are extracted in later slices.
Branch-variant and answer-to-record requests use shared feature single-flight
holders; Android still supplies navigation context and adapter execution.
Ask source-record navigation also uses a shared single-flight holder; Android maps
the holder's stable destination/history keys to its `Screen` navigation model.
Ask answer-generation request identity, live stream buffers, regeneration state,
and completion events live in `AskStreamStateHolder`; Android retains SSE and
device-local AI execution adapters.
Conversation/message loading and its retry message also live in the shared
`AskLoadStateHolder`.
The question draft and retrieval scope/source options live in the shared
`AskComposerStateHolder`.
All Ask request holders share a monotonic `AskSessionStateHolder` generation to
invalidate callbacks after navigation.
Automatic-summary persistence crosses `AIAutoSummaryRepository` and its shared
application use case; Android local and remote adapters retain SQLite and REST.
AI profile saves cross `AIProfilesRepository` and `SaveAIProfilesUseCase`;
Android adapters translate the shared write command to encrypted local storage
or REST and reconcile secret-free server responses with the local key cache.
AI settings loads cross `AISettingsRepository` and `LoadAISettingsUseCase` as a
consistent snapshot. The local adapter may attach its decrypted device key for
offline execution; the remote adapter returns secret-free server metadata.
Profile connection tests and remote model discovery cross focused application
capabilities. Device-local AI implements testing only; the REST diagnostics
adapter implements testing and model listing.
Its optimistic update, rollback, and single-flight request identity live in the
shared `kmp-features:settings` module.
AI profile editor drafts, raw numeric inputs, validation, and secret-safe save
response reconciliation also live in that module. Android keeps encrypted
storage, REST inputs, and device-local AI execution as adapters.
The root state composes `AIProfilesMutationStateHolder` for editable profiles,
optimistic save, rollback, and stale-callback rejection, with transitional read
accessors for the existing Compose screens.
It also composes `AISettingsLoadStateHolder`; settings loads and profile saves
have separate request identities and invalidate one another at their boundary.
Provider-test and model-list progress/results live in
`AIProfileDiagnosticsStateHolder`, which rejects callbacks after profile edits,
removal, mode changes, or client-context replacement.
Pure outbox, applied-result, conflict, and push-summary models live in
`kmp-core:sync`; Android owns their current JSON, REST, and transactional storage
adapters. Pending memo pushes run through the shared outbox/gateway use case.
Shared `kmp-features:sync` conflict state and core resolution commands own the explicit choice workflow;
Android retains the confirmation UI and transactional local-storage adapter.
Ask and secret-free AI settings values are imported directly from shared domain;
cross-platform AI profile drafts come from the settings feature, while
Android-local models are limited to API inputs and platform adapters.
Full pull runs through shared `SyncSnapshot` gateway/repository contracts and
`PullSyncUseCase`. Android maps REST pages and atomically merges the snapshot;
the versioned JSON export remains a separate adapter DTO and keeps its v1 schema.
Shared sync orchestration also owns attachment-preparation-before-push and the
push-then-pull order; Android supplies attachment staging and localized feedback.

See [Multiplatform Development](../../../docs/development/multiplatform.md) and
[Architecture](../../../docs/development/architecture.md) for module ownership and
dependency rules.

## Build and Test

JDK 17 and Android SDK 35 are required. The repository pins Gradle, locks every resolvable dependency graph, verifies downloaded dependency SHA-256 values, and scans the complete release runtime with OSV Scanner. Run the CI-equivalent host gate from the repository root:

```bash
make check-android
```

This runs shared native common tests, Android unit tests, Android Lint, debug
and instrumentation APK assembly, the release manifest policy, license-notice
drift checks, the release-runtime vulnerability scan, and a consistency check
that the CI device matrix still covers `minSdk` and `targetSdk`. To run
Keystore/SQLite migration and critical Compose journeys on a connected emulator
or physical device:

```bash
make check-android-device
```

CI provisions separate clean API 26 and API 35 x86_64 emulators and runs this device gate on both supported boundaries for every Android change. The device suite verifies real Android Keystore migration and encrypted database persistence, cold-relaunch offline record persistence, the Recently Deleted restore/permanent-delete journey, and access to the bundled open-source notices. Stable release candidates additionally require one physical-device smoke test because an emulator cannot validate OEM storage, keyboard, file-viewer, and lifecycle behavior completely.

The debug APK is located at:

```text
apps/native/androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Install it on a connected device or emulator:

```bash
cd apps/native
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## Release Signing

The application ID is `app.sillage`. Signing files are not committed to the repository. Under `apps/native/`, prepare `release.keystore` and `signing.properties`:

```properties
storeFile=release.keystore
storePassword=...
keyAlias=sillage-release
keyPassword=...
```

Build and verify the release APK:

```bash
cd apps/native
./gradlew :androidApp:assembleRelease
apksigner verify --verbose --print-certs androidApp/build/outputs/apk/release/androidApp-release.apk
zipalign -c -v 4 androidApp/build/outputs/apk/release/androidApp-release.apk
```

Before a release, increment `versionCode`, set `versionName` to the tag without its `v` prefix, and add the matching checked input at `.github/release-notes/vX.Y.Z.md`. The release build uses this keystore only when the local signing configuration exists. Do not publish artifacts with unverified signatures, and do not commit APK/AAB files, keystores, `signing.properties`, or `local.properties`.

## Security Boundaries

Only debug builds permit cleartext HTTP for LAN and emulator development. Release APKs reject cleartext traffic and require an HTTPS instance. Login sessions and offline data are protected through Android Keystore, but exported JSON contains sensitive data in plaintext and should be shared and stored only in restricted locations.

The APK includes the complete reviewed release-runtime dependency inventory and Apache 2.0, BSD 2-Clause, Mozilla Public License 2.0, and applicable NOTICE text. Users can read it under **Settings → About → Open-source licenses**. Regenerate it after dependency changes with `make generate-android-third-party-notices`; CI rejects stale or unreviewed license mappings.

Attachment links accept only standard external `http(s)` URLs or same-origin `/file/attachments/...` paths. The app downloads protected attachments to its cache with authentication, then passes them to the system viewer through a read-only FileProvider URI.

See the [Contributing Guide](../../../CONTRIBUTING.md) for the complete development gates, and the [Deployment Guide](../../../docs/user/deployment.md) and [Data, Backup, and Recovery](../../../docs/user/data.md) for server deployment and data security.
