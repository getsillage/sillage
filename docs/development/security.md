# Security Development Boundaries

This document records the stable boundaries that must be preserved when changing security-sensitive Sillage code. See the root [Security Policy](../../SECURITY.md) for vulnerability reporting, the [Deployment Guide](../user/deployment.md) for deployment security, and [Data, Backup, and Recovery](../user/data.md) for data protection.

## Assets and Trust Boundaries

Protected assets include records, attachments, Ask history, account credentials, login sessions, AI API keys, and runtime secrets. The primary boundaries are:

```text
Web / Android -> operator-managed HTTPS entry point or trusted LAN -> Sillage -> SQLite / attachments
                                                                       |
                                                                       +-> configured AI provider
```

Sillage itself serves HTTP only. A separately operated HTTPS entry point is responsible for public TLS, sanitizing forwarded headers, and isolating the backend port. Public ingress, DNS, tunneling, CDNs, and other edge-network services remain outside the product and repository; Sillage does not ship connectors or vendor-specific configuration for them. The host, complete data directory, external secrets, and custom AI provider are all trust domains explicitly selected by the operator.

## Authentication and Sessions

- An instance may create only one non-deleted account. The initialization check and write must remain in the same transaction.
- The initialization endpoint is unauthenticated before the instance has an account. Deployment documentation must require initialization on a loopback address and confirmation of bootstrap state before exposing an external entry point or LAN port. An uninitialized instance must never be exposed directly to the public Internet.
- Passwords are stored only as derived hashes and must never appear in logs, responses, or sync data. New passwords contain at least 8 Unicode characters and at most 256 UTF-8 bytes; there is no forced composition rule, and existing shorter passwords remain verifiable until changed.
- Authenticated password change requires the current password and a different new password (`POST /api/v1/auth/change-password`). On success the server updates the hash and revokes every refresh session in one transaction, then issues a new token pair for the caller. There is no unauthenticated forgot-password flow.
- Break-glass recovery is a local-only `admin reset-password` command. It requires the service to be stopped, the same exclusive data-directory lock used by the server, an explicit username, and a regular owner-only password file; it never exposes an unauthenticated HTTP route or accepts the password as a command-line value. The password update and refresh-session revocation are atomic.
- Access tokens are signed by the server and expire after 15 minutes. Refresh tokens are stored only as hashes, expire after 30 days, and rotate on refresh. Signing out revokes the refresh session, but an already issued access token remains usable until it expires.
- Cookies must retain `HttpOnly` and `SameSite=Lax`. They must also use `Secure` under TLS or trusted `X-Forwarded-Proto: https`.
- Stored Argon2 password hashes are parsed as a canonical, fixed resource profile before verification; malformed, duplicate, unknown, or resource-amplifying parameters fail closed before Argon2 runs.
- Protected business write endpoints accept only Bearer tokens. Cookie fallback is limited to safe GET requests where browsers cannot set an Authorization header, such as attachment reads, and must not be extended to business writes.
- Sign-in rate limiting uses both account and client IP. Forwarded headers are ignored unless the direct peer matches an explicitly configured `SILLAGE_TRUSTED_PROXY` CIDR. A trusted proxy must overwrite rather than append client-supplied forwarded headers.

## Data and Secrets

- `SESSION_SECRET` signs sessions; `ENCRYPTION_SECRET` derives the envelope-encryption key for AI API keys. Their generation, file permissions, and recovery semantics must not change silently.
- AI API keys are written to SQLite only as encrypted envelopes. REST, Connect, sync, logs, and exports must never return plaintext keys.
- `runtime/secrets.json` is not a cache. Secret rotation or storage-format changes require compatibility or explicit migration, backup, and rollback instructions.
- The database, attachments, and backups do not have full at-rest encryption. Do not describe field-level AI API key encryption as complete data encryption.
- Deleting an AI profile must clear the API key envelope from the current database row. Retention semantics for historical backups, record tombstones, and AI-derived data must remain explicit in the user data documentation.
- Resource tombstones are retained for offline convergence. Ephemeral session/runtime rows and 90-day sync idempotency rows are cleaned periodically; attachment tombstones retain metadata but not deleted file bytes.

## Attachments and Content

- Upload, read, and delete operations must authorize against the account. A UID, filename, or disk path alone is never sufficient authorization.
- Uploads limit the HTTP body, the multipart file declaration, and the number of bytes actually copied. A current non-empty MIME type comes from the client and must be treated as untrusted metadata; it cannot relax authorization or filesystem boundaries.
- Filenames must be stripped of paths and unsafe characters. A database `storage_ref` must also be validated before reads and deletes so it cannot escape the data directory.
- The current inline allowlist is `image/*`, `text/plain`, and PDF; every other type is forced to download. Changes to this set require a separate evaluation of active content such as SVG and must retain `X-Content-Type-Options: nosniff`.
- Web Markdown does not execute raw HTML and filters dangerous URL schemes. Changes to the renderer, links, or attachment previews require XSS and cross-account access tests.

## AI and External Requests

- Only the authenticated account may manage AI profiles and custom base URLs. A custom address may reach any network target available to the service runtime, so it is trusted configuration and must never become unauthenticated or third-party-controlled input.
- A summary request sends one record body. An Ask turn normally makes two requests to the same configured provider. The routing request sends its system prompt, the current question, and current branch history, but does not attach record bodies, summaries, or excerpts. The answer request sends the question and branch history again; it attaches relevant source excerpts or summaries only for `records` and `mixed`, never for `general`. Any change to these scopes must update [AI Usage and Privacy](../user/ai.md).
- Routing output is untrusted and must be parsed against the expected `general` / `records` / `mixed` shape. A parse failure must fall back to `records`, and generated retrieval phrases may only select records locally within the authenticated account and requested time scope.
- Record excerpts and conversation content are untrusted data, not instructions. Routing and answer prompts must delimit them accordingly, must not treat prior assistant text as evidence of a personal fact, and must retain only valid citations to sources actually used in the answer.
- API keys may appear only in the authentication headers required by the provider. Logs, error responses, and test failure output must not contain keys, Authorization headers, or request bodies.
- Normal generation endpoints return stable user-facing errors and must not pass through provider response bodies. Connection tests may include diagnostic information, but they must still filter credentials and request content.

## Logs and Probes

- Request logs contain only request ID, method, path, status, duration, and client IP. They do not contain headers, bodies, cookies, tokens, or record content.
- `/healthz` and `/readyz` do not require authentication. `readyz` currently includes dependency error text. Error chains must not introduce secrets, account data, record content, or other sensitive configuration, and public deployments should evaluate whether to reduce diagnostic detail.
- Before adding an error log, confirm that its error chain cannot contain secrets, private text, or provider request payloads.
- Web drafts are stored in browser `localStorage`; they are not included in server backups and may remain after sign-out. Changes to draft or sign-out behavior must keep this boundary visible and avoid misleading users on shared devices.

## Request and Response Limits

- Non-multipart requests are limited to 8 MiB at the HTTP middleware boundary. Attachment uploads use the configured `SILLAGE_MAX_UPLOAD_MB` limit and are independently bounded while copying bytes to disk.
- A record body is limited to 1 MiB of UTF-8 at both the server service and
  shared native new-write boundaries. Search strings are limited to 512 bytes,
  Ask questions to 64 KiB, and AI configuration fields to bounded sizes with
  documented validation errors. AI provider responses are capped before
  decoding, and generated text is capped before persistence. Existing native
  snapshots remain readable when a historical record violates a newer draft
  rule; compatibility reads must not silently rewrite or discard that content.
- The HTTP server enforces header, header-read, body-read, write, and idle timeouts. Streaming Ask responses use the longer write timeout but remain cancellable through the request context.

## Native Public Server Discovery and Authentication

- The iOS, Windows, and macOS engineering clients may call only the public
  `GET /api/v1/auth/bootstrap` endpoint before authentication. The request sends
  a versioned user agent and JSON accept header, but no record content,
  credentials, cookies, authorization header, or request body.
- Server addresses are user-controlled trusted configuration. Embedded URL
  credentials, query strings, fragments, unsupported schemes, whitespace, and
  empty authorities fail before transport. Bootstrap bodies are bounded and
  mapped into application values; HTTP response bodies are never surfaced in
  user-facing errors.
- A successful check stores the normalized address in the private native client
  snapshot. The private memo outbox separately binds cloud versions and mutation
  identifiers to one normalized address. Portable record backups export neither
  address nor synchronization metadata and preserve the current device address
  when restored.
- Published clients must use operator-managed HTTPS. Plain HTTP is limited to
  explicit loopback or trusted-LAN engineering use; iOS additionally enforces
  App Transport Security. This unauthenticated diagnostic does not establish a
  session or authorize later requests.
- After a successful public check, desktop and iOS may send initialization or
  sign-in credentials only to that normalized server address. Generic HTTP
  request/response diagnostics expose method, URL, header names, status, and
  sizes, but redact bodies and every header value so passwords, bearer tokens,
  cookies, and server error bodies are not rendered by `toString()`. Native
  transports do not follow redirects; operators must configure the final
  server URL before credentials are sent.
- Native authentication disables automatic platform cookie handling. The
  shared repository extracts only `sillage_refresh`, sends it only to the
  repository's validated base URL, keeps the access token and active refresh
  credential in a generation-checked memory session, and retries one 401 after
  rotating the refresh session. A replaced session cannot be cleared by an
  older request.
- Authenticated iOS, Windows, and macOS clients send password changes only to
  the normalized server's fixed `POST /api/v1/auth/change-password` path.
  Current, new, and confirmation values stay in transient UI/request state and
  never enter snapshots, backups, logs, or credential stores. The operation is
  single-flight and context-bound, and blocks record writes, synchronization,
  and sign-out until it completes. Success stores the rotated refresh
  credential before accepting the new memory session, clears all password
  drafts, and informs the user that every other session was revoked. Ordinary
  failures retain the drafts for retry; session expiry or secure-storage
  failure clears the displayed authenticated account and requires sign-in.
- Shared native memo synchronization is created by the same authentication
  factory. It sends Bearer credentials only to the normalized server's fixed
  `GET /api/v1/sync` and `POST /api/v1/sync:push` paths. Pull requests use the
  server limit of 200, percent-encode opaque cursors, and stop only after a
  terminal page; push requests contain at most 200 changes, retry at most once
  after refresh, and reject missing, reordered, or mismatched results. Create
  and update send the record fields required by the write; delete, restore, and
  purge omit the record body. Request diagnostics still redact bodies and every
  header value; no access token or record body enters durable session storage.
  The local workspace refuses to open synchronization state bound to another
  server, and the application permits sync only for the currently checked,
  authenticated address. The application root may invoke one foreground sync
  immediately after an account becomes authenticated; it uses the same checked
  address and session-expiry path as a manual request. Record writes are locked
  for the duration of sync so pushed acknowledgements and pulled records cannot
  race a local snapshot write.
- `AuthenticationCredentialStore` is the only shared cross-launch credential
  boundary and accepts only the refresh credential. Access tokens, passwords,
  and refresh credentials are never written to the client snapshot, ordinary
  preferences, plaintext files, environment, command arguments, or portable
  backup. There is no plaintext fallback.
- iOS stores the refresh credential as a non-synchronizing Security.framework
  Generic Password item whose account is the normalized server URL and whose
  accessibility is `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`. The app
  performs public bootstrap before using it, persists every rotation before
  accepting the new memory session, and deletes malformed or server-rejected
  values. Keychain failures surface as `SecureStorageUnavailable` while manual
  sign-in remains available. `ThisDeviceOnly` prevents device migration, but
  iOS may retain Keychain items across app reinstallation; uninstalling is not
  guaranteed sign-out.
- macOS stores the refresh credential as a Generic Password item under the
  same service and normalized server account, with
  `kSecAttrSynchronizable=false`. The JVM host calls modern
  Security.framework `SecItem*` APIs through JNA and never passes token
  material to the `security` CLI. Keychain failures use the same stable
  `SecureStorageUnavailable` reason. A macOS Keychain item may outlive removal
  of the application bundle, so uninstalling is not guaranteed sign-out.
- Windows stores the refresh credential as a `CRED_TYPE_GENERIC`,
  `CRED_PERSIST_LOCAL_MACHINE` item in the current user's Credential Manager.
  Its target is the application service prefix plus the normalized server URL.
  The JVM host calls `CredReadW`, `CredWriteW`, `CredDeleteW`, and
  `CredFree` through JNA and never passes token material to `cmdkey`,
  PowerShell, or process arguments. The local-machine persistence mode is
  non-roaming, and vault failures surface as `SecureStorageUnavailable`.
  Credential Manager items may outlive application removal, so uninstalling is
  not guaranteed sign-out.
- Online sign-out deletes the captured durable refresh credential before
  requesting server revocation. If deletion fails, the remote request is not
  sent and the current session remains visible with a secure-storage error.
  After deletion succeeds, remote failure or cancellation clears only the
  captured memory session; it never clears a newer session created while the
  request was in flight.

## Android

- Release APKs require HTTPS. Cleartext HTTP is enabled only in debug builds for emulators and trusted LAN development.
- Login data and local AI API keys are encrypted through Android Keystore. Offline records, Ask data, attachment metadata, and sync state are independently AES-GCM encrypted before entering the SQLite WAL database; database and WAL files must not contain plaintext user content.
- The first database open migrates former encrypted or plaintext `sillage.local_data` SharedPreferences values. Migration is idempotent and must not replace a database value that already exists. Unreadable ciphertext or a lost Keystore key fails closed, retains the raw payload for diagnosis, and must never be interpreted as an empty library.
- Multi-key state transitions that couple content and sync metadata must use one SQLite transaction. Android automatic backup remains disabled. Exported JSON must remove API keys and clearly warn that all remaining content is still sensitive plaintext.
- Protected attachments are downloaded with authentication into the application cache, then passed to external viewers through read-only FileProvider URIs. Private application file paths must never be exposed.
- Gradle dependency locks and verification metadata are release inputs. The APK release runtime must pass OSV scanning, every runtime coordinate must have a reviewed license mapping, and the generated notices must remain accessible from Settings.

## Changes and Validation

Security-related changes must cover the relevant tests at a minimum:

- authentication, cookies, or proxy headers: `server/auth_*_test.go`, `server/connect_routes_test.go`;
- attachments: cross-account access, path traversal, size limits, download responses, and cleanup;
- secrets or AI profiles: encryption and decryption, unavailable secrets, and absence of plaintext in responses;
- AI data scope or prompts: update the server and Android together and verify the user privacy documentation;
- Android storage or exports: Robolectric transaction/reopen tests, real-device Keystore and migration tests, cold-relaunch persistence, and export redaction.

See the [Contributing Guide](../../CONTRIBUTING.md) for complete commands. Implementation sources of truth are `server/auth/`, `server/auth_routes.go`, `server/attachment_routes.go`, `server/ai_provider*.go`, `internal/secret/`, `store/`, `apps/web/src/components/Markdown.tsx`, and the Android `data/` layer.
