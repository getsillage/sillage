# Application core

Platform-neutral repository ports and use cases for native clients.

The records slice currently exposes six distinct boundaries:

- `RecordsRepository` and `ListRecordsUseCase` read one consistent local
  snapshot without exposing storage implementation types.
- `RecordsPageRepository` and `ListRecordsPageUseCase` request a semantic,
  server-backed page without exposing HTTP parameters or transport DTOs.
- `RecordsSearchRepository` and `SearchRecordsUseCase` perform semantic
  full-text search against a selected local or remote source.
- `RecordDetailRepository` and `GetRecordDetailUseCase` load one record and
  its AI-derived metadata without exposing storage or transport response types.
- `RecordWriteRepository` and `SaveRecordUseCase` create or update a record
  from a platform-neutral draft command.
- `RecordLifecycleRepository` and `MutateRecordLifecycleUseCase` archive,
  favorite, delete, restore, or permanently delete a canonical record.

Android provides `LocalRecordsRepository` and `RemoteRecordsRepository`
adapters. Other platform hosts implement the same ports using their own storage
and transport clients. Shared APIs must not expose Android, SQLite, JSON, HTTP,
or generated protocol types.
