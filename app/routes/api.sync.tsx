import { env } from "cloudflare:workers";
import { toAttachmentDto, toEntryDto } from "~/lib/api/serialize";
import { requireSession } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import { EMPTY_CURSOR, getChangesSince, type StreamCursor, type SyncCursor } from "~/lib/db/sync";
import type { Route } from "./+types/api.sync";

// Max valid ECMAScript Date timestamp; rejecting beyond this keeps a hostile cursor
// from constructing an Invalid Date that would corrupt the SQL comparison.
const MAX_TIMESTAMP_MS = 8.64e15;

function isStreamCursor(value: unknown): value is StreamCursor {
  if (!value || typeof value !== "object") {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.updatedAt === "number" &&
    Number.isFinite(candidate.updatedAt) &&
    candidate.updatedAt >= 0 &&
    candidate.updatedAt <= MAX_TIMESTAMP_MS &&
    typeof candidate.id === "string"
  );
}

/** Decodes the opaque base64 cursor token. Any malformed value restarts from the beginning. */
function decodeCursor(raw: string | null): SyncCursor {
  if (!raw) {
    return EMPTY_CURSOR;
  }
  try {
    const parsed: unknown = JSON.parse(atob(raw));
    if (parsed && typeof parsed === "object") {
      const obj = parsed as Record<string, unknown>;
      return {
        entries: isStreamCursor(obj.entries) ? obj.entries : null,
        attachments: isStreamCursor(obj.attachments) ? obj.attachments : null,
      };
    }
  } catch {
    // Malformed token (bad base64/JSON) — fall through to a full resync.
  }
  return EMPTY_CURSOR;
}

function encodeCursor(cursor: SyncCursor): string {
  return btoa(JSON.stringify(cursor));
}

/**
 * Delta-sync endpoint for non-web clients (e.g. a mobile app):
 * `GET /api/sync?cursor=<opaque>` returns everything changed since the cursor,
 * including soft-deleted rows, plus an opaque `cursor` to pass back next time.
 * Omit `cursor` for a full snapshot.
 */
export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const url = new URL(request.url);
  const cursor = decodeCursor(url.searchParams.get("cursor"));
  const db = getDb(env.DB);
  const changes = await getChangesSince(db, cursor);

  return Response.json({
    entries: changes.entries.map(toEntryDto),
    attachments: changes.attachments.map(toAttachmentDto),
    cursor: encodeCursor(changes.cursor),
    hasMore: changes.hasMore,
  });
}
