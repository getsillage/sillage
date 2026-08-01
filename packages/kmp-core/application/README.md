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
generation and device-local AI execution remain platform adapter concerns until
their asynchronous boundaries are extracted separately.

The settings slice exposes `AIAutoSummaryRepository` and
`SetAIAutoSummaryUseCase` for the independently persisted automatic-summary
preference. `AIProfilesRepository` and `SaveAIProfilesUseCase` accept an explicit
write command and return secret-free domain metadata. Parsed numeric input stays
nullable for transport omission semantics, while local adapters retain the last
valid stored value. Local and remote Android adapters own encrypted storage and
REST translation.

Android provides `LocalRecordsRepository` and `RemoteRecordsRepository`
adapters. Other platform hosts implement the same ports using their own storage
and transport clients. Shared APIs must not expose Android, SQLite, JSON, HTTP,
or generated protocol types.
