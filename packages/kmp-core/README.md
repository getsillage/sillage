# Kotlin Multiplatform core

Reserved for platform-independent native-client foundations:

- domain models and policies;
- application use cases and repository ports;
- remote transport adapters;
- local database and migrations;
- synchronization state machine and conflict persistence;
- cryptography and secure-storage abstractions.

Platform source sets may implement drivers and operating-system integrations,
but common code must not depend on Android, Apple, or desktop UI frameworks.
