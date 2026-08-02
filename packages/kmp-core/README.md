# Kotlin Multiplatform core

Platform-independent native-client foundations. `domain`, `application`,
`local-data`, and `sync` are buildable Kotlin Multiplatform modules; remaining
directories are reserved module boundaries:

- domain models and policies;
- application use cases and repository ports;
- portable client snapshot codec and local record repository;
- remote transport adapters;
- local database migrations;
- synchronization state machine and conflict persistence;
- cryptography and secure-storage abstractions.

Platform source sets may implement drivers and operating-system integrations,
but common code must not depend on Android, Apple, or desktop UI frameworks.
