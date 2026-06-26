import { desc, eq } from "drizzle-orm";
import type { Db } from "./client";
import { uuidv7 } from "./id";
import { type EntryRevision, entryRevisions } from "./schema";

/** The content captured for one point in an entry's edit history. */
export interface RevisionSnapshot {
  entryDate: string;
  body: string;
}

export interface EntryRevisionView {
  id: string;
  version: number;
  entryDate: string;
  body: string;
  createdAt: Date;
}

function toView(row: EntryRevision): EntryRevisionView {
  return {
    id: row.id,
    version: row.version,
    entryDate: row.entryDate,
    body: row.body,
    createdAt: row.createdAt,
  };
}

/** Appends an immutable snapshot of an entry version to its history. */
export async function recordEntryRevision(
  db: Db,
  entryId: string,
  version: number,
  snapshot: RevisionSnapshot,
  at: Date = new Date(),
): Promise<void> {
  await db.insert(entryRevisions).values({
    id: uuidv7(),
    entryId,
    version,
    entryDate: snapshot.entryDate,
    body: snapshot.body,
    createdAt: at,
  });
}

/** Lists an entry's edit history, newest version first. */
export async function listEntryRevisions(db: Db, entryId: string): Promise<EntryRevisionView[]> {
  const rows = await db
    .select()
    .from(entryRevisions)
    .where(eq(entryRevisions.entryId, entryId))
    .orderBy(desc(entryRevisions.version));
  return rows.map(toView);
}
