# Security Development Boundaries

This document records the stable boundaries that must be preserved when changing security-sensitive Sillage code. See the root [Security Policy](../../SECURITY.md) for vulnerability reporting, the [Deployment Guide](../user/deployment.md) for deployment security, and [Data, Backup, and Recovery](../user/data.md) for data protection.

## Assets and Trust Boundaries

Protected assets include records, attachments, Ask history, account credentials, login sessions, AI API keys, and runtime secrets. The primary boundaries are:

```text
Web / Android -> HTTPS proxy or trusted LAN -> Sillage -> SQLite / attachments
                                                |
                                                +-> configured AI provider
```

Sillage itself serves HTTP only. A reverse proxy or tunnel is responsible for public TLS, sanitizing forwarded headers, and isolating the backend port. The host, complete data directory, external secrets, and custom AI provider are all trust domains explicitly selected by the operator.

## Authentication and Sessions

- An instance may create only one non-deleted account. The initialization check and write must remain in the same transaction.
- The initialization endpoint is unauthenticated before the instance has an account. Deployment documentation must require initialization on a loopback address and confirmation of bootstrap state before exposing a proxy, tunnel, or LAN port. An uninitialized instance must never be exposed directly to the public Internet.
- Passwords are stored only as derived hashes and must never appear in logs, responses, or sync data.
- Access tokens are signed by the server and expire after 15 minutes. Refresh tokens are stored only as hashes, expire after 30 days, and rotate on refresh. Signing out revokes the refresh session, but an already issued access token remains usable until it expires.
- Cookies must retain `HttpOnly` and `SameSite=Lax`. They must also use `Secure` under TLS or trusted `X-Forwarded-Proto: https`.
- Protected business write endpoints accept only Bearer tokens. Cookie fallback is limited to safe GET requests where browsers cannot set an Authorization header, such as attachment reads, and must not be extended to business writes.
- Sign-in rate limiting uses both account and client IP. The application reads `X-Forwarded-For`, so the proxy must overwrite rather than append client-supplied forwarded headers.

## Data and Secrets

- `SESSION_SECRET` signs sessions; `ENCRYPTION_SECRET` derives the envelope-encryption key for AI API keys. Their generation, file permissions, and recovery semantics must not change silently.
- AI API keys are written to SQLite only as encrypted envelopes. REST, Connect, sync, logs, and exports must never return plaintext keys.
- `runtime/secrets.json` is not a cache. Secret rotation or storage-format changes require compatibility or explicit migration, backup, and rollback instructions.
- The database, attachments, and backups do not have full at-rest encryption. Do not describe field-level AI API key encryption as complete data encryption.
- Deleting an AI profile must clear the API key envelope from the current database row. Retention semantics for historical backups, record tombstones, and AI-derived data must remain explicit in the user data documentation.

## Attachments and Content

- Upload, read, and delete operations must authorize against the account. A UID, filename, or disk path alone is never sufficient authorization.
- Uploads limit the HTTP body, the multipart file declaration, and the number of bytes actually copied. A current non-empty MIME type comes from the client and must be treated as untrusted metadata; it cannot relax authorization or filesystem boundaries.
- Filenames must be stripped of paths and unsafe characters. A database `storage_ref` must also be validated before reads and deletes so it cannot escape the data directory.
- The current inline allowlist is `image/*`, `text/plain`, and PDF; every other type is forced to download. Changes to this set require a separate evaluation of active content such as SVG and must retain `X-Content-Type-Options: nosniff`.
- Web Markdown does not execute raw HTML and filters dangerous URL schemes. Changes to the renderer, links, or attachment previews require XSS and cross-account access tests.

## AI and External Requests

- Only the authenticated account may manage AI profiles and custom base URLs. A custom address may reach any network target available to the service runtime, so it is trusted configuration and must never become unauthenticated or third-party-controlled input.
- A summary request sends one record body. Ask sends the question, current branch history, and source excerpts. Any change to these scopes must update [AI Usage and Privacy](../user/ai.md).
- API keys may appear only in the authentication headers required by the provider. Logs, error responses, and test failure output must not contain keys, Authorization headers, or request bodies.
- Normal generation endpoints return stable user-facing errors and must not pass through provider response bodies. Connection tests may include diagnostic information, but they must still filter credentials and request content.

## Logs and Probes

- Request logs contain only request ID, method, path, status, duration, and client IP. They do not contain headers, bodies, cookies, tokens, or record content.
- `/healthz` and `/readyz` do not require authentication. `readyz` currently includes dependency error text. Error chains must not introduce secrets, account data, record content, or other sensitive configuration, and public deployments should evaluate whether to reduce diagnostic detail.
- Before adding an error log, confirm that its error chain cannot contain secrets, private text, or provider request payloads.
- Web drafts are stored in browser `localStorage`; they are not included in server backups and may remain after sign-out. Changes to draft or sign-out behavior must keep this boundary visible and avoid misleading users on shared devices.

## Android

- Production instances use HTTPS only. HTTP support is a compatibility boundary for emulators and trusted LANs, not a recommendation for public deployment.
- Login data, offline data, and local AI API keys are encrypted through Android Keystore. Legacy plaintext SharedPreferences remain readable and migrate on the next save. Exported JSON must remove API keys and clearly warn that all remaining content is still sensitive plaintext.
- Protected attachments are downloaded with authentication into the application cache, then passed to external viewers through read-only FileProvider URIs. Private application file paths must never be exposed.

## Changes and Validation

Security-related changes must cover the relevant tests at a minimum:

- authentication, cookies, or proxy headers: `server/auth_*_test.go`, `server/connect_routes_test.go`;
- attachments: cross-account access, path traversal, size limits, download responses, and cleanup;
- secrets or AI profiles: encryption and decryption, unavailable secrets, and absence of plaintext in responses;
- AI data scope or prompts: update the server and Android together and verify the user privacy documentation;
- Android storage or exports: Keystore compatibility, legacy-data migration, and export redaction.

See the [Contributing Guide](../../CONTRIBUTING.md) for complete commands. Implementation sources of truth are `server/auth/`, `server/auth_routes.go`, `server/attachment_routes.go`, `server/ai_provider*.go`, `internal/secret/`, `store/`, `web/src/components/Markdown.tsx`, and the Android `data/` layer.
