import { asc, eq, gt } from "drizzle-orm";
import type { Db } from "./client";
import { composeEntries, type EntryWithTags } from "./entries";
import { type Attachment, attachments, entries, entryAi } from "./schema";

/**
 * Delta-sync read model. Returns everything that changed after `since` — including
 * soft-deleted rows, so an offline client can mirror removals — ordered by the
 * `updatedAt` watermark. The returned `cursor` is the high-water mark to pass back
 * on the next poll.
 *
 * Single-user, low-write workload: a millisecond `updatedAt` watermark with a strict
 * `>` comparison is sufficient. (A multi-writer system would want a monotonic
 * server sequence to disambiguate same-millisecond writes.)
 */
export interface SyncChanges {
  entries: EntryWithTags[];
  attachments: Attachment[];
  cursor: number;
  hasMore: boolean;
}

const DEFAULT_LIMIT = 200;

function maxUpdatedAt(values: Date[], fallback: number): number {
  return values.reduce((max, value) => Math.max(max, value.getTime()), fallback);
}

export async function getChangesSince(
  db: Db,
  since: Date,
  limit: number = DEFAULT_LIMIT,
): Promise<SyncChanges> {
  const entryRows = await db
    .select()
    .from(entries)
    .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(gt(entries.updatedAt, since))
    .orderBy(asc(entries.updatedAt))
    .limit(limit);

  const attachmentRows = await db
    .select()
    .from(attachments)
    .where(gt(attachments.updatedAt, since))
    .orderBy(asc(attachments.updatedAt))
    .limit(limit);

  const changedEntries = await composeEntries(db, entryRows);

  const cursor = maxUpdatedAt(
    [...changedEntries.map((entry) => entry.updatedAt), ...attachmentRows.map((a) => a.updatedAt)],
    since.getTime(),
  );

  return {
    entries: changedEntries,
    attachments: attachmentRows,
    cursor,
    hasMore: entryRows.length === limit || attachmentRows.length === limit,
  };
}
