# REST API Guide

This document defines the stable usage boundaries of Echo REST v1. The implementation sources of truth are `server/*_routes.go` and the REST behavior tests. See the [Contributing Guide](../../../CONTRIBUTING.md) for the change workflow.

## Contract Sources

- `proto/api/v1/` is the source of the Connect contract. `buf` generates Connect, Gateway, and `proto/gen/openapi/openapi.yaml`.
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
| Authentication | `server/auth_routes.go` | Initialization, sign-in, refresh, sign-out, and the current account |
| Records and sync | `server/memo_routes.go`, `server/sync_routes.go` | `memoDTO` uses `createdAt`, `updatedAt`, and a numeric `version` |
| Attachments | `server/attachment_routes.go` | Multipart upload, metadata, deletion, and authenticated download |
| AI settings | `server/ai_routes.go` | Configuration, model listing, connection tests, and automatic summaries |
| Ask | `server/ask_routes.go` | Conversations, messages, branches, head, and SSE streaming answers |

SSE routes return `text/event-stream`. Uploads, attachment downloads, SSE, and action-style `POST` endpoints are all handwritten REST extensions; changes must update both this table and the tests.

Ask message objects returned by list, create, stream, and sync routes use camelCase fields. `promptVersion` identifies the prompt semantics used to generate an answer: newly generated assistant answers use `ask-answer-v2`, while user messages and historical rows that were not backfilled return an empty string. `sourceRefs` contains only valid record citations retained from the answer and is empty for a general answer. The SSE `done` event carries the same message shape as the non-streaming routes.

## Versioning and Compatibility

`/api/v1` permits only backward-compatible additions of fields, optional parameters, and endpoints. Removing or renaming contract elements, changing a field's type or meaning, or changing the authentication or error model requires a new version path. The release notes must document migration and rollback requirements.

Proto changes must run `buf lint`, `buf breaking`, and `buf generate`. When REST and Connect share semantics, tests must cover both transports. Handwritten REST-only extensions must also retain equivalent REST regression coverage.
