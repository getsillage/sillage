# Network

Buildable Kotlin Multiplatform network boundary for native clients. The first
vertical slice owns public server bootstrap URL validation, HTTP status
handling, JSON mapping, and application-model conversion through
`RemoteInstanceBootstrapRepository`.

Platform hosts provide the small `SillageHttpTransport` implementation so
Foundation and JVM networking APIs stay outside shared application and feature
modules. Authentication cookies, secure sessions, authenticated REST/SSE,
refresh coordination, and retry policy remain follow-up slices. Wire payloads
must always be mapped before they reach feature state.
