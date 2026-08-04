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
the native document picker and file I/O adapters. Appearance, Data, and About
cards reuse `shared-ui:settings`; this module maps localized native copy,
platform metadata, data-location content, optional backup actions, and their
capability gates into those shared presentations.

The About card exposes host-packaged third-party notices when available and uses
the shared selectable licenses dialog. Missing notice resources remain a host
packaging failure rather than application-owned fallback content.

Its Service settings surface owns public server-address editing, bounded
bootstrap request identity, late-result rejection, connection status, and
bilingual errors. After a successful check it also owns initialization/sign-in
form state, startup restore request identity, stable authentication error copy,
in-memory account presentation, automatic and manual memo two-way synchronization, conflict
presentation and resolution, authenticated password change, and sign-out
orchestration. Password change reuses the shared single-flight auth lifecycle,
blocks record writes and sync while active, clears password drafts on success,
and retains them for retry after ordinary failures. Session expiry or a failed
secure credential rotation returns the client to sign-in instead of presenting
a stale authenticated account. Manual sync is
available only for the checked and authenticated server, locks record writes
while active, pushes before pulling, refreshes canonical server records, and
preserves rejected or conflicting mutations.
Hosts provide HTTP transport, credential-vault capability, and repository
composition only. Successful checks persist the normalized address without
silently rebinding an existing outbox. Automatic bootstrap and restore run only
when the host advertises cross-launch authentication persistence.

When an account becomes authenticated, whenever the authenticated app later
enters the foreground, or when its network status recovers from unavailable to
available while foregrounded, the application root runs automatic push-then-pull
memo synchronization through the same single-flight controller path. Routine
success and no-change results do not replace authentication feedback; conflicts,
rejections, session expiry, and failures remain visible. Ask and attachments
remain follow-up integration slices. iOS and macOS supply Keychain adapters,
while Windows supplies a Credential Manager adapter. All three advertise
cross-launch authentication persistence and share the same automatic and manual
sync paths.
