# Application

Shared use cases, repository ports, transaction boundaries, and application
orchestration. Application code depends on domain types and declared ports, not
on concrete HTTP, SQLite, or platform implementations.

The first vertical slice declares `RecordsRepository` and
`ListRecordsUseCase` for reading one consistent record snapshot. Android
provides `LocalRecordsRepository` as its persistence adapter; storage mapping
and encryption remain outside this module.
