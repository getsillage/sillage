# Network

Buildable Kotlin Multiplatform network boundary for native clients. Public
bootstrap owns server URL validation, HTTP status handling, JSON mapping, and
application-model conversion through `RemoteInstanceBootstrapRepository`.
`RemoteInstanceAuthenticationRepositoryFactory` creates base-URL-scoped
repositories for initialization, sign-in, bearer requests, one-time refresh,
password change, and context-bound sign-out.

Platform hosts provide the small `SillageHttpTransport` implementation so
Foundation and JVM networking APIs stay outside shared application and feature
modules. Request and response diagnostics redact bodies and header values.
Refresh cookies and access tokens currently stay only in a generation-checked
memory session; platform secure persistence, authenticated record REST/SSE, and
broader retry coordination remain follow-up slices. Wire payloads must always be
mapped before they reach feature state.
