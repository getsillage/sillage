# Native secure session restoration

## Context

The shared native authentication repository already owns initialization,
sign-in, refresh rotation, password change, and context-bound sign-out. Its
generation-checked memory session prevents stale asynchronous work from
overwriting a newer sign-in, but closing a native application discards every
credential and otherwise requires another password entry.

Persistent login is a security boundary rather than an ordinary client
preference. An access token does not need to survive the process, while the one
refresh credential used to mint a new session must never enter the private
record snapshot, a portable backup, an environment variable, a command line,
or a plaintext file. Vault availability and migration behavior differ by
operating system, so unsupported hosts need an explicit memory-only default
instead of an insecure compatibility fallback.

Session restoration must continue to trust only the normalized server address,
perform public capability discovery before sending a credential, persist every
refresh rotation, and reject late results after the client context changes.
Sign-out must not report success while a still-valid durable credential can
restore the session on a later launch.

## Decision

`kmp-core:network` defines `AuthenticationCredentialStore`, keyed by the
normalized server base URL and limited to one refresh credential value. Its
default implementation is memory-only and reports that it cannot persist
across launches. A host may report persistent authentication only when it
supplies an operating-system credential-vault adapter. Plaintext files,
preference stores, environment variables, command arguments, portable backups,
and automatic fallbacks are forbidden.

The active access token and refresh credential remain in the
generation-checked memory session. After initialization, sign-in, password
change, or refresh, the repository writes the rotated refresh credential to the
vault before accepting the new memory session. A vault write failure clears the
new memory session and returns the stable `SecureStorageUnavailable` reason.
A malformed stored value or refresh `401` deletes the obsolete credential;
vault read or delete failures use the same stable reason rather than silently
continuing.

The iOS host stores one Security.framework Generic Password item. Its service
is `app.sillage.native.authentication.refresh`, its account is the normalized
server URL, and its accessibility class is
`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`. The item is explicitly
non-synchronizing and is not included in the application's record snapshot or
portable backup.

The macOS desktop host stores the same Generic Password service and account in
the current user's Keychain with `kSecAttrSynchronizable=false`. The JVM host
uses JNA to call modern Security.framework `SecItemCopyMatching`,
`SecItemUpdate`, `SecItemAdd`, and `SecItemDelete` APIs directly. It does not
use deprecated `SecKeychain*` APIs or invoke the `security` command-line tool.
Core Foundation objects are released on every path, and transient UTF-8 byte
arrays and JNA memory are cleared after transfer. Update-then-add behavior
retries an update after a duplicate-item race.

Only the refresh credential is stored on either platform; access tokens and
passwords remain process-memory-only. When a host advertises persistent
authentication, application startup loads the saved server address, performs
unauthenticated bootstrap discovery, and only then exchanges a stored refresh
credential for a newly rotated session. A missing or expired credential
returns to the sign-in form without an error. A vault failure remains visible
while manual sign-in stays available. Hosts using the memory-only adapter do
not perform automatic startup networking.

Online sign-out first deletes the captured session's durable refresh
credential and only then requests server revocation using the captured
in-memory value. If vault deletion fails, the remote request is not sent and
the authoritative memory session remains visible with a secure-storage error.
Once deletion has succeeded, network failure or cancellation may clear only
the captured memory session; it cannot clear or delete credentials written by
a newer sign-in.

The iOS test target defines real Keychain round-trip, rotation, and deletion
coverage for a simulator or device. The macOS JVM test executes the same
round-trip, rotation, deletion, and invalid-value behavior against the user's
real Keychain on macOS. Windows remains memory-only until a Credential Manager
adapter provides equivalent fail-closed behavior and real-vault coverage.

## Consequences

iOS and macOS can resume a valid session after relaunch without persisting an
access token or password. Restoration requires the relevant Keychain to be
available and the server to be reachable. On iOS,
`ThisDeviceOnly` prevents the credential from migrating to another device.
The macOS item is non-synchronizing but is not device-bound.

Keychain items may outlive removal of the application. Uninstalling Sillage is
therefore not guaranteed sign-out on either persistent host; users must use
in-app sign-out or server-side session revocation when that guarantee is
required.

Loss, denial, or corruption of secure-vault access is explicit and fail-closed.
There is no recovery path that copies a refresh credential into less secure
storage. A successful sign-in can replace an obsolete item once vault access is
available again.

Persistent hosts perform one public bootstrap request and, when a credential
is present, one refresh request during startup. Windows continues to require
sign-in after each launch until its operating-system adapter and real-vault
tests land.
