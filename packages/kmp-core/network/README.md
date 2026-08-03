# Network

Buildable Kotlin Multiplatform network boundary for native clients. Public
bootstrap owns server URL validation, HTTP status handling, JSON mapping, and
application-model conversion through `RemoteInstanceBootstrapRepository`.
`RemoteInstanceAuthenticationRepositoryFactory` creates base-URL-scoped
repositories for initialization, sign-in, bearer requests, one-time refresh,
password change, stored-session restoration, and context-bound sign-out.

Platform hosts provide the small `SillageHttpTransport` implementation so
Foundation and JVM networking APIs stay outside shared application and feature
modules. Request and response diagnostics redact bodies and header values.
Access tokens and the active refresh credential stay in a generation-checked
memory session. `AuthenticationCredentialStore` is the only optional durable
boundary and stores only the refresh credential; its default is memory-only.
iOS injects a Security.framework Keychain implementation, while other hosts can
add native vault adapters without changing repository or feature contracts.
Every refresh rotation is persisted before the new memory session is accepted,
and durable deletion precedes online sign-out. Authenticated record REST/SSE and
broader retry coordination remain follow-up slices. Wire payloads must always be
mapped before they reach feature state.
