# Native secure session restoration

## Context

The shared native authentication repository already owns initialization,
sign-in, refresh rotation, password change, and context-bound sign-out. Its
generation-checked memory session prevents stale asynchronous work from
overwriting a newer sign-in, but closing a native application discards every
credential and requires another password entry.

Persistent login is a security boundary rather than ordinary client
preferences. An access token does not need to survive a process, while the one
refresh credential used to mint a new session must never enter the private
record snapshot, portable backup, environment, command line, or a plaintext
file. Native vault availability and migration behavior also differ by operating
system, so unsupported hosts need an explicit memory-only default instead of an
insecure compatibility fallback.

Session restoration must continue to trust only a normalized server address,
perform public capability discovery before sending a credential, persist every
refresh rotation, and reject late results after the client context changes.
Sign-out must not report success while a still-valid durable credential can
restore the session on a later launch.

## Decision

`kmp-core:network` defines `AuthenticationCredentialStore`, keyed by the
normalized server base URL and limited to the refresh credential value. The
default implementation is memory-only and reports that it cannot persist across
launches. A host may report persistent authentication only when it supplies an
operating-system credential-vault adapter. Plaintext files, preference stores,
environment variables, command arguments, portable backups, and automatic
fallbacks are forbidden.

The active access token and refresh credential remain in the
generation-checked memory session. After initialization, sign-in, password
change, or refresh, the repository writes the rotated refresh credential to the
vault before accepting the memory session. A vault write failure clears the new
memory session and returns the stable `SecureStorageUnavailable` reason. A
malformed stored value or a refresh `401` deletes the obsolete credential;
vault read or delete failures also use the stable failure reason rather than
silently continuing.

The iOS host stores one Security.framework Generic Password item. Its service is
`app.sillage.native.authentication.refresh`, its account is the normalized
server URL, and its accessibility class is
`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`. The item is not synchronizable
and is not included in the application's record snapshot or portable backup.
Only the refresh credential is stored; access tokens and passwords remain
process memory only. Core Foundation values created or returned by the adapter
are released explicitly.

When the host advertises persistent authentication, application startup loads
the saved server address, performs unauthenticated bootstrap discovery, and only
then exchanges the stored refresh credential for a newly rotated session. A
missing or expired credential returns to the sign-in form without an error. A
vault failure remains visible while manual sign-in stays available. Hosts using
the memory-only adapter do not perform automatic startup networking.

Online sign-out first deletes the captured session's durable refresh credential
and only then requests server revocation using the captured in-memory value. If
vault deletion fails, the remote request is not sent and the authoritative
memory session remains visible with a secure-storage error. Once deletion has
succeeded, network failure or cancellation may clear only that captured memory
session; it cannot clear or delete credentials written by a newer sign-in.

The iOS adapter has a simulator/device test that exercises real Keychain
round-trip, rotation, and deletion. Windows and macOS remain memory-only until
native Credential Manager and Keychain adapters provide equivalent behavior.
Token-bearing command-line utilities, including the macOS `security` CLI, are
not acceptable implementations because they would expose credentials in process
arguments.

## Consequences

iOS can resume a valid session after relaunch without persisting an access token
or password. Restoration requires the device to be unlocked and the server to
be reachable, and `ThisDeviceOnly` prevents the credential from migrating to a
different device. iOS may retain Keychain items across application
reinstallation, so uninstalling the app is not a guaranteed sign-out; users must
use in-app sign-out or server-side session revocation when that guarantee is
required.

Loss, denial, or corruption of secure-vault access is explicit and fail-closed.
There is no recovery path that copies the refresh credential into less secure
storage. A successful sign-in can replace an obsolete item once vault access is
available again.

Persistent hosts perform one public bootstrap request and, when a credential is
present, one refresh request during startup. Desktop Windows and macOS continue
to require sign-in after each launch until their operating-system adapters and
real-vault tests land.
