# Application core

Platform-neutral repository ports and use cases for native clients.

The records slice currently exposes two distinct boundaries:

- `RecordsRepository` and `ListRecordsUseCase` read one consistent local
  snapshot without exposing storage implementation types.
- `RecordsPageRepository` and `ListRecordsPageUseCase` request a semantic,
  server-backed page without exposing HTTP parameters or transport DTOs.

Android provides `LocalRecordsRepository` and `RemoteRecordsRepository`
adapters. Other platform hosts implement the same ports using their own storage
and transport clients. Shared APIs must not expose Android, SQLite, JSON, HTTP,
or generated protocol types.
