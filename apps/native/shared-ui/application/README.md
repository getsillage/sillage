# Shared native application

`application` is the Compose Multiplatform composition root used by desktop and
iOS hosts. It assembles the shared application shell, records workspace, local
record controller, appearance settings, responsive navigation, and host action
ports without depending on filesystem, Foundation, Android, or window APIs.

Online authentication, remote synchronization, Ask, attachments, and secure
credential adapters remain follow-up integration slices. The current host is a
functional device-local records workspace rather than a simulated server.
