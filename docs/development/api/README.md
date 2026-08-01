# REST API Guide

This document defines the stable usage boundaries of Echo REST v1. The implementation sources of truth are `server/*_routes.go` and the REST behavior tests. See the [Contributing Guide](../../../CONTRIBUTING.md) for the change workflow.

## Contract Sources

- `contracts/proto/api/v1/` is the source of the Connect contract. `buf` generates Connect, Gateway, and `contracts/proto/gen/openapi/openapi.yaml`.
- The generated OpenAPI document reflects only the Proto HTTP annotations and may omit handwritten Echo routes, the authentication model, and REST DTOs. It cannot be used directly for REST SDK code generation.
- The route implementations and this document define REST v1 field names, status codes, and error responses. If a machine-readable REST OpenAPI document is needed, first complete the full specification and contract tests before treating it as a public input.

## Authentication and Errors

Except for `GET /healthz`, `GET /readyz`, and authentication bootstrap/initialize/signin/refresh/signout, application REST endpoints use:

```http
Authorization: Bearer <access_token>
```

Protected reads initiated natively by the browser, currently `/file/attachments/...`, may fall back to an HttpOnly access cookie. Cookies cannot be used for application write operations. Echo REST errors for unauthenticated requests, validation failures, conflicts, rate limits, and similar conditions use a consistent structure:

```json
{
  "error": {
    "code": "stable_machine_code",
    "message": "Localized user-facing message"
  }
}
```

The message value is localized, user-facing text. The example above is a placeholder, not a fixed response. Current application messages are in Simplified Chinese. Tests for affected routes must cover both the HTTP status and `error.code`. Connect errors use Connect codes and do not need to reuse this JSON structure.

## REST Surface

| Area | Route source of truth | Notes |
| --- | --- | --- |
| Authentication | `server/auth_routes.go`, `contracts/proto/api/v1/auth_service.proto` | Initialization, sign-in, refresh, sign-out, password change, and the current account |
| Records and sync | `server/memo_routes.go`, `server/sync_routes.go` | `memoDTO` uses `createdAt`, `updatedAt`, and a numeric `version` |
| Attachments | `server/attachment_routes.go` | Multipart upload, metadata, deletion, and authenticated download |
| AI settings | `server/ai_routes.go` | Configuration, model listing, connection tests, and automatic summaries |
| Ask | `server/ask_routes.go` | Conversations, messages, branches, head, and SSE streaming answers |

SSE routes return `text/event-stream`. Uploads, attachment downloads, SSE, and action-style `POST` endpoints are all handwritten REST extensions; changes must update both this table and the tests.

Ask message objects returned by list, create, stream, and sync routes use camelCase fields. `promptVersion` identifies the prompt semantics used to generate an answer: newly generated assistant answers use `ask-answer-v2`, while user messages and historical rows that were not backfilled return an empty string. `sourceRefs` contains only valid record citations retained from the answer and is empty for a general answer. The SSE `done` event carries the same message shape as the non-streaming routes.

Authentication password changes are exposed through both the REST route
`POST /api/v1/auth/change-password` and the Connect `AuthService.ChangePassword`
method. Both transports verify the current password, revoke other refresh
sessions, and return the rotated token pair for the current client.

New passwords must contain at least 8 Unicode characters and at most 256 UTF-8
bytes. Non-multipart requests over 8 MiB are rejected with HTTP 413; attachment
uploads use the separate configured upload limit. Record, Ask, search, and AI
configuration fields have stricter per-field limits and return the normal
`invalid_field` error when exceeded.

## Record Lifecycle

Normal record listing and search omit deleted records. `GET /api/v1/memos?deleted=true` returns recoverable records newest-deletion first; it excludes already purged tombstones and does not subdivide the lifecycle view by favorite/archive state. The response shape includes both `deletedAt` and `purgedAt`. A pagination cursor is tied to the filter set that produced it and must not be reused across normal and Recently Deleted views.

- `DELETE /api/v1/memos/{id}?expectedVersion={version}` moves a record to Recently Deleted.
- `POST /api/v1/memos/{id}:restore` with `{"expectedVersion": version}` restores a recoverable record.
- `POST /api/v1/memos/{id}:purge` with `{"expectedVersion": version}` permanently scrubs a deleted record.

The corresponding Connect methods are `DeleteMemo`, `RestoreMemo`, and `PurgeMemo`. Restore and purge require optimistic concurrency and return the normalized record. After the 30-day recovery window, server maintenance performs purge automatically. Purged rows retain a minimal tombstone for synchronization; they do not reappear in normal or Recently Deleted REST lists. See the [Sync API](sync.md) for offline convergence semantics.

## Versioning and Compatibility

`/api/v1` permits only backward-compatible additions of fields, optional parameters, and endpoints. Removing or renaming contract elements, changing a field's type or meaning, or changing the authentication or error model requires a new version path. The release notes must document migration and rollback requirements.

`GET /api/v1/auth/bootstrap` is public and returns initialization state plus operational compatibility metadata:

```json
{
  "initialized": true,
  "serverVersion": "v0.3.0",
  "serverRevision": "0123456789abcdef",
  "apiVersion": "v1",
  "minimumAndroidVersionCode": 9
}
```

The route is served with `Cache-Control: no-store`. Every HTTP response also carries `X-Sillage-Version`, `X-Sillage-Revision`, `X-Sillage-API-Version`, and `X-Sillage-Min-Android-Version-Code`. Web requests identify their build with `X-Sillage-Client`, `X-Sillage-Client-Version`, and `X-Sillage-Client-Revision`; these headers are diagnostic metadata, not authentication. Clients must tolerate additive bootstrap fields and must not infer compatibility from a display version alone: `apiVersion` and the platform-specific minimum remain authoritative. Android blocks online connection and synchronization when its `versionCode` is below `minimumAndroidVersionCode`, links users to GitHub Releases, and keeps Offline mode available.

Proto changes must run `buf lint`, `buf breaking`, and `buf generate`. When REST and Connect share semantics, tests must cover both transports. Handwritten REST-only extensions must also retain equivalent REST regression coverage.
