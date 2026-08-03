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

The records editor consumes the shared record-draft validator and maps its
empty-content, UTF-8 1 MiB, and invalid-date results to localized field-level
feedback. The controller applies these checks only when saving; records already
loaded from an older snapshot remain visible and editable.

Its Data settings surface also owns portable-backup actions, destructive restore
confirmation, busy-state gating, and localized success/failure feedback. Restore
remains available after snapshot hydration fails, while export and record
mutations stay disabled until valid data has been restored. Hosts retain only
the native document picker and file I/O adapters.

Its Service settings surface owns public server-address editing, bounded
bootstrap request identity, late-result rejection, connection status, and
bilingual errors. After a successful check it also owns initialization/sign-in
form state, startup restore request identity, stable authentication error copy,
in-memory account presentation, and sign-out orchestration. Hosts provide HTTP
transport, credential-vault capability, and repository composition only.
Successful checks persist the normalized address without changing the
device-local records mode. Automatic bootstrap and restore run only when the
host advertises cross-launch authentication persistence.

Remote record synchronization, Ask, attachments, and additional OS-backed
credential adapters remain follow-up integration slices. iOS and macOS supply
Keychain adapters and advertise cross-launch authentication persistence;
Windows intentionally retains the memory-only default. The current host is a
functional device-local records workspace rather than a simulated server.
