import { env } from "cloudflare:workers";
import { toAttachmentDto, toEntryDto } from "~/lib/api/serialize";
import { requireSession } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import { getChangesSince } from "~/lib/db/sync";
import type { Route } from "./+types/api.sync";

/** Parses a `since` cursor: pure-digit values are ms-epoch, anything else ISO 8601. */
function parseSince(raw: string | null): Date {
  if (!raw) {
    return new Date(0);
  }
  const trimmed = raw.trim();
  if (/^\d+$/.test(trimmed)) {
    return new Date(Number(trimmed));
  }
  const parsed = new Date(trimmed);
  return Number.isNaN(parsed.getTime()) ? new Date(0) : parsed;
}

/**
 * Delta-sync endpoint for non-web clients (e.g. a mobile app):
 * `GET /api/sync?since=<iso|ms>` returns everything changed since the cursor,
 * including soft-deleted rows, plus a `cursor` to pass back next time.
 */
export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const url = new URL(request.url);
  const since = parseSince(url.searchParams.get("since"));
  const db = getDb(env.DB);
  const changes = await getChangesSince(db, since);

  return Response.json({
    entries: changes.entries.map(toEntryDto),
    attachments: changes.attachments.map(toAttachmentDto),
    cursor: new Date(changes.cursor).toISOString(),
    hasMore: changes.hasMore,
  });
}
