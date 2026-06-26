import { and, desc, eq, isNull } from "drizzle-orm";
import type { Db } from "./client";
import { uuidv7 } from "./id";
import { type RevisionSnapshot, recordEntryRevision } from "./revisions";
import { type Entry, type EntryAi, entries, entryAi } from "./schema";

export interface EntryInput {
  entryDate: string;
  body: string;
}

export interface EntryWithAi extends Entry {
  // AI-derived fields are read from the `entry_ai` side table via a join; they are
  // null until the pipeline fills them in.
  summary: string | null;
  sentiment: string | null;
  // Provenance + cost of the current insight, surfaced in the UI's meta line.
  aiModel: string | null;
  aiDurationMs: number | null;
  aiGeneratedAt: Date | null;
  aiGenerationCount: number;
}

export type UpdateOutcome =
  | { status: "updated"; version: number }
  | { status: "missing" }
  | { status: "conflict"; currentVersion: number };

type JoinedRow = { entries: Entry; entry_ai: EntryAi | null };

/** Attaches AI side-table fields to joined entry rows. */
export function composeEntries(rows: JoinedRow[]): EntryWithAi[] {
  return rows.map((row) => ({
    ...row.entries,
    summary: row.entry_ai?.summary ?? null,
    sentiment: row.entry_ai?.sentiment ?? null,
    aiModel: row.entry_ai?.model ?? null,
    aiDurationMs: row.entry_ai?.durationMs ?? null,
    aiGeneratedAt: row.entry_ai?.generatedAt ?? null,
    aiGenerationCount: row.entry_ai?.generationCount ?? 0,
  }));
}

function revisionSnapshot(input: EntryInput): RevisionSnapshot {
  return {
    entryDate: input.entryDate,
    body: input.body,
  };
}

/** Creates an entry and returns the new id. */
export async function createEntry(db: Db, input: EntryInput): Promise<string> {
  const id = uuidv7();
  const now = new Date();
  await db.insert(entries).values({
    id,
    entryDate: input.entryDate,
    body: input.body,
    version: 1,
    createdAt: now,
    updatedAt: now,
  });
  await recordEntryRevision(db, id, 1, revisionSnapshot(input), now);
  return id;
}

/**
 * Updates an entry's content with optimistic concurrency. When
 * `expectedVersion` is provided and no longer matches, the write is rejected as a
 * conflict instead of clobbering a newer copy.
 */
export async function updateEntry(
  db: Db,
  id: string,
  input: EntryInput,
  expectedVersion?: number,
): Promise<UpdateOutcome> {
  const [existing] = await db
    .select({ version: entries.version })
    .from(entries)
    .where(and(eq(entries.id, id), isNull(entries.deletedAt)));
  if (!existing) {
    return { status: "missing" };
  }
  if (expectedVersion !== undefined && existing.version !== expectedVersion) {
    return { status: "conflict", currentVersion: existing.version };
  }

  const nextVersion = existing.version + 1;
  const now = new Date();
  const patch: Partial<typeof entries.$inferInsert> = {
    entryDate: input.entryDate,
    body: input.body,
    version: nextVersion,
    updatedAt: now,
  };

  // Compare-and-swap on the version we just read: if another writer slipped in
  // between the read above and here, zero rows match and we report a conflict
  // instead of silently clobbering the newer copy (lost update).
  const updated = await db
    .update(entries)
    .set(patch)
    .where(
      and(eq(entries.id, id), isNull(entries.deletedAt), eq(entries.version, existing.version)),
    )
    .returning({ id: entries.id });
  if (updated.length === 0) {
    const [current] = await db
      .select({ version: entries.version })
      .from(entries)
      .where(and(eq(entries.id, id), isNull(entries.deletedAt)));
    return current
      ? { status: "conflict", currentVersion: current.version }
      : { status: "missing" };
  }

  await recordEntryRevision(db, id, nextVersion, revisionSnapshot(input), now);
  return { status: "updated", version: nextVersion };
}

/**
 * Soft-deletes an entry: sets the tombstone and bumps `updatedAt` so sync clients
 * learn about the removal. The FTS trigger drops it from the keyword index;
 * attachments are preserved so the delete can be undone.
 */
export async function deleteEntry(db: Db, id: string): Promise<void> {
  const now = new Date();
  await db
    .update(entries)
    .set({ deletedAt: now, updatedAt: now })
    .where(and(eq(entries.id, id), isNull(entries.deletedAt)));
}

/** Restores a soft-deleted entry. No-op if the entry is live or missing. */
export async function restoreEntry(db: Db, id: string): Promise<void> {
  await db
    .update(entries)
    .set({ deletedAt: null, updatedAt: new Date() })
    .where(eq(entries.id, id));
}

/** Permanently removes an entry and its cascaded rows (attachments, AI). */
export async function purgeEntry(db: Db, id: string): Promise<void> {
  await db.delete(entries).where(eq(entries.id, id));
}

export async function getEntry(db: Db, id: string): Promise<EntryWithAi | null> {
  const rows = await db
    .select()
    .from(entries)
    .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(and(eq(entries.id, id), isNull(entries.deletedAt)));
  if (rows.length === 0) {
    return null;
  }
  const [composed] = composeEntries(rows);
  return composed ?? null;
}

/** Lists live entries newest-first (by entry date, then creation time). */
export async function listEntries(db: Db, limit = 50): Promise<EntryWithAi[]> {
  const rows = await db
    .select()
    .from(entries)
    .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(isNull(entries.deletedAt))
    .orderBy(desc(entries.entryDate), desc(entries.createdAt))
    .limit(limit);
  return composeEntries(rows);
}
