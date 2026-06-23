import { desc, eq } from "drizzle-orm";
import type { Db } from "./client";
import { type Entry, entries } from "./schema";
import { getTagsForEntries, setEntryTags } from "./tags";

export interface EntryInput {
  entryDate: string;
  title: string;
  body: string;
  mood?: number | null;
  weather?: string | null;
  tags: string[];
}

export interface EntryWithTags extends Entry {
  tags: string[];
}

/** Creates an entry with its tags and returns the new id. */
export async function createEntry(db: Db, input: EntryInput): Promise<string> {
  const id = crypto.randomUUID();
  const now = new Date();
  await db.insert(entries).values({
    id,
    entryDate: input.entryDate,
    title: input.title,
    body: input.body,
    mood: input.mood ?? null,
    weather: input.weather ?? null,
    createdAt: now,
    updatedAt: now,
  });
  await setEntryTags(db, id, input.tags);
  return id;
}

/** Updates an entry's content and tags. Returns false if the entry is missing. */
export async function updateEntry(db: Db, id: string, input: EntryInput): Promise<boolean> {
  const existing = await db.select({ id: entries.id }).from(entries).where(eq(entries.id, id));
  if (existing.length === 0) {
    return false;
  }
  await db
    .update(entries)
    .set({
      entryDate: input.entryDate,
      title: input.title,
      body: input.body,
      mood: input.mood ?? null,
      weather: input.weather ?? null,
      updatedAt: new Date(),
    })
    .where(eq(entries.id, id));
  await setEntryTags(db, id, input.tags);
  return true;
}

export async function deleteEntry(db: Db, id: string): Promise<void> {
  // entry_tags / attachments cascade via FK; FTS stays in sync via trigger.
  await db.delete(entries).where(eq(entries.id, id));
}

export async function getEntry(db: Db, id: string): Promise<EntryWithTags | null> {
  const [entry] = await db.select().from(entries).where(eq(entries.id, id));
  if (!entry) {
    return null;
  }
  const tagMap = await getTagsForEntries(db, [id]);
  return { ...entry, tags: tagMap.get(id) ?? [] };
}

/** Lists entries newest-first (by entry date, then creation time) with tags. */
export async function listEntries(db: Db, limit = 50): Promise<EntryWithTags[]> {
  const rows = await db
    .select()
    .from(entries)
    .orderBy(desc(entries.entryDate), desc(entries.createdAt))
    .limit(limit);
  const tagMap = await getTagsForEntries(
    db,
    rows.map((row) => row.id),
  );
  return rows.map((row) => ({ ...row, tags: tagMap.get(row.id) ?? [] }));
}
