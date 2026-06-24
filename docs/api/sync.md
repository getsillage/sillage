# Sync API

Delta-sync endpoint for non-web clients (e.g. a mobile app). It returns everything
that changed since a cursor — **including soft-deleted rows** — so a client can keep
a local mirror of the diary up to date with a single repeatable call.

> Status: **read-only** today. Entries already carry a `version` for optimistic
> concurrency (see [Writing back](#writing-back-future)), but write endpoints are
> not part of this API yet.

## Authentication

The same single-password session that gates the web app guards this endpoint.

- Authenticate by `POST`ing the password to `/login`; the server sets an
  **HttpOnly** session cookie (opaque id; the secret never reaches the client).
- Send that cookie on every `/api/sync` request.
- Unauthenticated requests are redirected (`302 → /login`). Treat any non-`200`
  response as "not authenticated / retry login", not as sync data.

## Endpoint

```
GET /api/sync?since=<cursor>
```

### `since` cursor

| Form | Example | Meaning |
|------|---------|---------|
| omitted / empty | `/api/sync` | full snapshot (everything, from epoch) |
| pure digits | `?since=1782208000000` | Unix epoch **milliseconds** |
| anything else | `?since=2026-06-24T02:00:00.000Z` | ISO 8601 timestamp |

Pass back the `cursor` from the previous response verbatim. The server returns it as
an ISO 8601 string; either form is accepted on the way back in.

The comparison is **strict** (`updatedAt > since`), so re-sending the last cursor
will not re-deliver the rows you already have.

## Response

`200 OK`, `application/json`:

```jsonc
{
  "entries": [ /* EntryDto, oldest-changed first */ ],
  "attachments": [ /* AttachmentDto */ ],
  "cursor": "2026-06-24T02:00:00.000Z", // high-water mark; send as `since` next time
  "hasMore": false                       // true => a page was full; poll again immediately
}
```

- `entries` are ordered by ascending `updatedAt` (the sync watermark).
- Each page returns at most **200** entries and **200** attachments. When
  `hasMore` is `true`, immediately request again with the returned `cursor` to
  drain the backlog before going idle.

### EntryDto

| Field | Type | Notes |
|-------|------|-------|
| `id` | string | UUIDv7 (time-sortable); stable across clients |
| `entryDate` | string | `YYYY-MM-DD`, the calendar date the entry is "for" |
| `title` | string | |
| `body` | string | Markdown plaintext |
| `mood` | number \| null | 1–5 |
| `weather` | string \| null | |
| `isPinned` | boolean | |
| `utcOffsetMinutes` | number \| null | writer's UTC offset when saved; resolves `entryDate`'s local meaning |
| `metadata` | object \| null | forward-compatible client bag; parsed from stored JSON |
| `version` | number | optimistic-concurrency token; increments on each content edit |
| `tags` | string[] | sorted, de-duplicated tag names |
| `ai` | object | `{ summary: string \| null, sentiment: string \| null }` (machine-derived) |
| `createdAt` | string | ISO 8601 |
| `updatedAt` | string | ISO 8601; the field the cursor tracks |
| `deletedAt` | string \| null | **non-null ⇒ tombstone**: delete this entry locally |

### AttachmentDto

| Field | Type | Notes |
|-------|------|-------|
| `id` | string | UUIDv7 |
| `entryId` | string \| null | owning entry; null if uploaded before its entry exists |
| `url` | string | `"/attachments/<id>"` — session-guarded; decrypts & streams the bytes |
| `filename` | string | |
| `contentType` | string | |
| `size` | number | bytes (plaintext) |
| `sha256` | string \| null | hex digest of plaintext — integrity & dedup |
| `width` / `height` | number \| null | image dimensions when known |
| `status` | string | `"stored"` (or `"pending"` mid-upload) |
| `createdAt` / `updatedAt` | string | ISO 8601 |
| `deletedAt` | string \| null | non-null ⇒ tombstone (bytes already reclaimed) |

The internal R2 object key is **never** exposed; fetch bytes via `url`.

## Client sync algorithm

```text
cursor = load_saved_cursor()            // null on first run
loop:
  res = GET /api/sync?since=cursor
  for entry in res.entries:
    if entry.deletedAt != null: delete_local(entry.id)
    else:                       upsert_local(entry)   // includes its tags + ai
  for att in res.attachments:
    if att.deletedAt != null: delete_local_attachment(att.id)
    else:                     upsert_local_attachment(att)
  cursor = res.cursor
  save_cursor(cursor)
  if not res.hasMore: break               // caught up
```

Then poll on whatever cadence you like (foreground refresh, push wake, timer);
each call only ships what changed after `cursor`.

### Notes & guarantees

- **Tombstones, not gaps.** Deletes arrive as rows with a non-null `deletedAt`
  rather than silently disappearing, so an offline client can mirror removals.
  (Hard purges, if ever run server-side, are the exception.)
- **AI updates are quiet.** Regenerating a summary writes only the `entry_ai`
  side table and does **not** bump `entries.updatedAt` — so a summary refresh will
  not, by itself, re-deliver the entry. Re-fetch a specific entry if you need the
  latest `ai` fields immediately.
- **Single-writer watermark.** The cursor is a millisecond `updatedAt` value with a
  strict `>` comparison, sufficient for this single-user diary. It is not designed
  for concurrent multi-writer fan-out.

## Example

```bash
# 1. Log in, capturing the session cookie.
curl -c jar.txt -X POST https://<host>/login \
  --data-urlencode "password=$DIARY_PASSWORD"

# 2. Full snapshot.
curl -b jar.txt "https://<host>/api/sync"

# 3. Incremental: pass back the previous `cursor`.
curl -b jar.txt "https://<host>/api/sync?since=2026-06-24T02:00:00.000Z"
```

## Writing back (future)

There is no write endpoint yet, but the schema is ready for one:

- Clients can mint a UUIDv7 `id` locally (offline create, no server round-trip).
- Send the `version` you last read; the server's `updateEntry` rejects a stale
  write with a **conflict** (current vs expected version) instead of clobbering a
  newer copy. Resolve by re-fetching and reapplying.
- `metadata` / `utcOffsetMinutes` are preserved when a writer omits them, so a
  partial update from one client won't wipe another client's fields.
