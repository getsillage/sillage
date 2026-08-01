# Application core

Platform-neutral repository ports and use cases for native clients.

The records slice currently exposes seven distinct boundaries:

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
