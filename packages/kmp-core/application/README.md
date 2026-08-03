# Application core

Platform-neutral repository ports and use cases for native clients.

The auth slice owns token-bearing `AuthSession` and public `BootstrapInfo`
application values. Secret-free account identity lives in `kmp-core:domain`;
platform adapters retain HTTP and secure session persistence.
`InstanceBootstrapRepository` owns public instance capability discovery.
`AuthenticationRepository` and focused initialize, sign-in, current-account, and
password-change use cases own authentication intent.
`InstanceAuthenticationRepositoryFactory` creates a repository scoped to one
validated server address, while stable `AuthenticationFailureReason` values let
shared UI map failures without exposing server response bodies. Android's remote
adapter maps those contracts to REST while `SillageApi` retains session refresh
context-safe encrypted persistence. Desktop and iOS use the shared remote
repository and its optional refresh-credential store; iOS and macOS inject
Security.framework Keychain adapters, while Windows retains the memory-only
default. `InstanceAuthenticationRepository.restore` exchanges the stored
refresh credential for a newly rotated session without exposing it to feature
state.
`SignOutRepository` captures a session-bound capability before asynchronous
execution. `SignOutUseCase` owns offline clearing, remote-failure fallback, and
cancellation semantics without exposing tokens or platform session snapshots.
Persistent adapters delete the captured durable credential before remote
revocation; a deletion failure must remain visible instead of reporting a false
sign-out, and a rejected conditional clear means a newer session remains
authoritative.

The records slice currently exposes nine distinct boundaries:

- `RecordsRepository` and `ListRecordsUseCase` read one consistent local
  snapshot without exposing storage implementation types.
- `RecordsPageRepository` and `ListRecordsPageUseCase` request a semantic,
  server-backed page without exposing HTTP parameters or transport DTOs.
- `RecordsSearchRepository` and `SearchRecordsUseCase` perform semantic
  full-text search against a selected local or remote source.
- `RecordDetailRepository` and `GetRecordDetailUseCase` load one record and
  its AI-derived metadata without exposing storage or transport response types.
- `RecordWriteRepository` and `SaveRecordUseCase` create or update a record
  from a platform-neutral draft command for editor and Ask save flows.
- `RecordLifecycleRepository` and `MutateRecordLifecycleUseCase` archive,
  favorite, delete, restore, or permanently delete a canonical record.
- `RecordSummaryGenerator`, `RecordSummaryStore`, and their use cases separate
  AI generation from version-checked local persistence.
- `AttachmentUploadRepository` and `UploadAttachmentUseCase` carry filename,
  media type, bytes, and canonical uploaded metadata without exposing multipart,
  JSON, or HTTP client types.
- Generic `AttachmentDownloadRepository` and `DownloadAttachmentUseCase` stream
  authenticated content into a host-provided destination while common code owns
  only the request path and response metadata.

`SaveRecordUseCase` validates every new record draft before it crosses a write
port: content must be non-empty, no larger than 1 MiB after UTF-8 encoding, and
the entry date must be a valid `YYYY-MM-DD` date. These rules match the server
service and apply to creates and updates, including saves initiated by Ask.
They are new-write constraints, not snapshot migration rules: structurally
valid historical records remain readable even when their stored content or
date would fail current draft validation.

The Ask slice exposes `AskRepository` with focused use cases for listing
conversations, listing messages, creating a conversation, and selecting its
branch head. Android provides local and remote adapters. Streaming answer
delivery crosses `AskAnswerStreamer` and `StreamAskAnswerUseCase` as ordered
start, delta, and failure events. Android retains SSE parsing, HTTP, session
retry, and device-local AI execution in platform adapters. Offline generation
crosses `AskAnswerGenerator`; `AskTurnStore` persists the generated turn through
a separate capability. Shared records and settings use cases supply canonical
context without exposing local storage to the host ViewModel.

The settings slice exposes `AIAutoSummaryRepository` and
`SetAIAutoSummaryUseCase` for the independently persisted automatic-summary
preference. `AIProfilesRepository` and `SaveAIProfilesUseCase` accept an explicit
write command and return secret-free domain metadata. Parsed numeric input stays
nullable for transport omission semantics, while local adapters retain the last
valid stored value. Local and remote Android adapters own encrypted storage and
REST translation.
`AISettingsRepository` and `LoadAISettingsUseCase` read one consistent settings
snapshot. Each entry wraps canonical profile metadata and an optional secret
available to that device; the secret remains outside the domain entity and is
absent from remote responses.
`AIProfileConnectionTester` and `AIProfileModelCatalog` keep provider diagnostics
as separate capabilities. The local adapter implements connection testing with
the device AI client; the remote adapter implements testing and model discovery
through REST. Both consume the same `AIProfileConfigurationCommand` used by
profile saves.

Android provides `LocalRecordsRepository` and `RemoteRecordsRepository`
adapters. Other platform hosts implement the same ports using their own storage
and transport clients. Shared APIs must not expose Android, SQLite, JSON, HTTP,
or generated protocol types.

The preferences slice owns canonical theme/language/app-mode tokens and
normalization helpers. Platform adapters retain encrypted or OS preference
storage while sharing the same accepted values.
