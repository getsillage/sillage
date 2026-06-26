import { and, asc, eq, gt, or, type SQL } from "drizzle-orm";
import type { SQLiteColumn } from "drizzle-orm/sqlite-core";
import type { Db } from "./client";
import { composeEntries, type EntryWithAi } from "./entries";
import { type Attachment, attachments, entries, entryAi } from "./schema";

/**
 * Delta-sync read model. Returns everything that changed after the cursor —
 * including soft-deleted rows, so an offline client can mirror removals.
 *
 * Pagination is **keyset by `(updatedAt, id)`** per stream. The `id` tie-breaker is
 * what makes a page boundary that lands in the middle of rows sharing a single
 * millisecond resume correctly on the next request, instead of permanently skipping
 * the rest of that millisecond (which a bare `updatedAt > cursor` would do).
 */
export interface StreamCursor {
  updatedAt: number; // ms epoch of the last row returned for this stream
  id: string; // id of the last row returned for this stream (tie-breaker)
}

export interface SyncCursor {
  entries: StreamCursor | null;
  attachments: StreamCursor | null;
}

export interface SyncChanges {
  entries: EntryWithAi[];
  attachments: Attachment[];
  cursor: SyncCursor;
  hasMore: boolean;
}

/** Start-of-history cursor: a full snapshot. */
export const EMPTY_CURSOR: SyncCursor = { entries: null, attachments: null };

const DEFAULT_LIMIT = 200;

/** Keyset predicate: rows strictly after `(updatedAt, id)`. */
function afterCursor(
  updatedAtCol: SQLiteColumn,
  idCol: SQLiteColumn,
  cursor: StreamCursor | null,
): SQL | undefined {
  if (!cursor) {
    return undefined;
  }
  const ts = new Date(cursor.updatedAt);
  return or(gt(updatedAtCol, ts), and(eq(updatedAtCol, ts), gt(idCol, cursor.id)));
}

function nextStreamCursor(
  rows: ReadonlyArray<{ updatedAt: Date; id: string }>,
  previous: StreamCursor | null,
): StreamCursor | null {
  const last = rows.at(-1);
  return last ? { updatedAt: last.updatedAt.getTime(), id: last.id } : previous;
}

export async function getChangesSince(
  db: Db,
  cursor: SyncCursor = EMPTY_CURSOR,
  limit: number = DEFAULT_LIMIT,
): Promise<SyncChanges> {
  const entryRows = await db
    .select()
    .from(entries)
    .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(afterCursor(entries.updatedAt, entries.id, cursor.entries))
    .orderBy(asc(entries.updatedAt), asc(entries.id))
    .limit(limit);

  const attachmentRows = await db
    .select()
    .from(attachments)
    .where(afterCursor(attachments.updatedAt, attachments.id, cursor.attachments))
    .orderBy(asc(attachments.updatedAt), asc(attachments.id))
    .limit(limit);

  const changedEntries = composeEntries(entryRows);

  return {
    entries: changedEntries,
    attachments: attachmentRows,
    cursor: {
      entries: nextStreamCursor(
        entryRows.map((row) => row.entries),
        cursor.entries,
      ),
      attachments: nextStreamCursor(attachmentRows, cursor.attachments),
    },
    hasMore: entryRows.length === limit || attachmentRows.length === limit,
  };
}
