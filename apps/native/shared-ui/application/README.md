# Shared native application

`application` is the Compose Multiplatform composition root used by desktop
and iOS hosts. It assembles the shared application shell, records workspace,
local record controller, appearance settings, responsive navigation, and host
action ports without depending on filesystem, Foundation, Android, or window
APIs.

The module also exposes localized native-host menu copy, a read-only dirty-editor
signal, and the themed discard confirmation used by page navigation and desktop
window lifecycle actions. Hosts can integrate native menus and close requests
without duplicating draft-loss policy.

Its Data settings surface also owns portable-backup actions, destructive restore
confirmation, busy-state gating, and localized success/failure feedback. Hosts
retain only the native document picker and file I/O adapters.

Online authentication, remote synchronization, Ask, attachments, and secure
credential adapters remain follow-up integration slices. The current host is a
functional device-local records workspace rather than a simulated server.
