import { desc, eq } from "drizzle-orm";
import type { Db } from "./client";
import { uuidv7 } from "./id";
import { type EntryRevision, entryRevisions } from "./schema";

/** The content captured for one point in an entry's edit history. */
export interface RevisionSnapshot {
  entryDate: string;
  title: string;
  body: string;
  mood: number | null;
  moodText: string | null;
  weather: string | null;
  location: string | null;
  people: string[];
  relationships: string[];
  tags: string[];
}

/** The secondary fields stored as JSON alongside the title/body columns. */
interface RevisionFields {
  entryDate: string;
  mood: number | null;
  moodText: string | null;
  weather: string | null;
  location: string | null;
  people: string[];
  relationships: string[];
  tags: string[];
}

export interface EntryRevisionView {
  id: string;
  version: number;
  title: string;
  body: string;
  createdAt: Date;
  fields: RevisionFields;
}

const EMPTY_FIELDS: RevisionFields = {
  entryDate: "",
  mood: null,
  moodText: null,
  weather: null,
  location: null,
  people: [],
  relationships: [],
  tags: [],
};

function parseFields(raw: string | null): RevisionFields {
  if (!raw) {
    return EMPTY_FIELDS;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<RevisionFields>;
    return { ...EMPTY_FIELDS, ...parsed };
  } catch {
    return EMPTY_FIELDS;
  }
}

function toView(row: EntryRevision): EntryRevisionView {
  return {
    id: row.id,
    version: row.version,
    title: row.title,
    body: row.body,
    createdAt: row.createdAt,
    fields: parseFields(row.fields),
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
  const fields: RevisionFields = {
    entryDate: snapshot.entryDate,
    mood: snapshot.mood,
    moodText: snapshot.moodText,
    weather: snapshot.weather,
    location: snapshot.location,
    people: snapshot.people,
    relationships: snapshot.relationships,
    tags: snapshot.tags,
  };
  await db.insert(entryRevisions).values({
    id: uuidv7(),
    entryId,
    version,
    title: snapshot.title,
    body: snapshot.body,
    fields: JSON.stringify(fields),
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
