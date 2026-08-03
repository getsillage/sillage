# Network

Buildable Kotlin Multiplatform network boundary for native clients. Public
bootstrap owns server URL validation, HTTP status handling, JSON mapping, and
application-model conversion through `RemoteInstanceBootstrapRepository`.
`RemoteInstanceAuthenticationRepositoryFactory` creates base-URL-scoped
repositories for initialization, sign-in, bearer requests, one-time refresh,
password change, stored-session restoration, and context-bound sign-out.

The same factory creates `MemoSyncGateway` adapters backed by the same
generation-checked authentication session. `RemoteMemoSyncGateway` maps
`create`, `update`, `delete`, `restore`, and `purge`, splits pending mutations
into requests of at most 200 changes, bounds and validates ordered server
results, and returns shared applied/conflict/rejected values. Transactional
outbox persistence and conflict storage remain platform adapters.

Platform hosts provide a small `SillageHttpTransport` implementation so
Foundation and JVM networking APIs stay outside shared application and feature
modules. Request and response diagnostics redact bodies and header values.
Access tokens and the active refresh credential stay in the generation-checked
memory session. `AuthenticationCredentialStore` is the only optional durable
boundary and stores only the refresh credential; the default is memory-only.
iOS and macOS inject Security.framework Keychain implementations, while
Windows injects a WinCred Credential Manager implementation. Every refresh
rotation is persisted before the new memory session is accepted, and durable
deletion precedes online sign-out. Authenticated record reads, SSE, and broader
retry coordination remain follow-up slices. Wire payloads must always be mapped
before they reach feature state.
