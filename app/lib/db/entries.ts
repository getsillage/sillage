import { and, desc, eq, inArray, isNull, like, type SQL } from "drizzle-orm";
import { serializeTextList } from "~/lib/product/entry-fields";
import type { Db } from "./client";
import { uuidv7 } from "./id";
import { type RevisionSnapshot, recordEntryRevision } from "./revisions";
import { type Entry, type EntryAi, entries, entryAi, entryTags, tags } from "./schema";
import { getTagsForEntries, setEntryTags } from "./tags";

export interface EntryInput {
  entryDate: string;
  title: string;
  body: string;
  mood?: number | null;
  moodText?: string | null;
  weather?: string | null;
  location?: string | null;
  people?: string[];
  relationships?: string[];
  tags: string[];
  // Optional, client-supplied. Omit the key entirely to leave the stored value
  // untouched on update (so a web edit never wipes mobile-set fields).
  utcOffsetMinutes?: number | null;
  metadata?: Record<string, unknown> | null;
}

export interface EntryWithTags extends Entry {
  // AI-derived fields are read from the `entry_ai` side table via a join; they are
  // null until the pipeline fills them in.
  summary: string | null;
  sentiment: string | null;
  // Provenance + cost of the current insight, surfaced in the UI's meta line.
  aiModel: string | null;
  aiDurationMs: number | null;
  aiGeneratedAt: Date | null;
  aiGenerationCount: number;
  tags: string[];
}

export type UpdateOutcome =
  | { status: "updated"; version: number }
  | { status: "missing" }
  | { status: "conflict"; currentVersion: number };

type JoinedRow = { entries: Entry; entry_ai: EntryAi | null };

/** Attaches AI side-table fields and tags to joined entry rows. */
export async function composeEntries(db: Db, rows: JoinedRow[]): Promise<EntryWithTags[]> {
  const tagMap = await getTagsForEntries(
    db,
    rows.map((row) => row.entries.id),
  );
  return rows.map((row) => ({
    ...row.entries,
    summary: row.entry_ai?.summary ?? null,
    sentiment: row.entry_ai?.sentiment ?? null,
    aiModel: row.entry_ai?.model ?? null,
    aiDurationMs: row.entry_ai?.durationMs ?? null,
    aiGeneratedAt: row.entry_ai?.generatedAt ?? null,
    aiGenerationCount: row.entry_ai?.generationCount ?? 0,
    tags: tagMap.get(row.entries.id) ?? [],
  }));
}

function serializeMetadata(metadata: Record<string, unknown> | null | undefined): string | null {
  return metadata ? JSON.stringify(metadata) : null;
}

function revisionSnapshot(input: EntryInput): RevisionSnapshot {
  return {
    entryDate: input.entryDate,
    title: input.title,
    body: input.body,
    mood: input.mood ?? null,
    moodText: input.moodText ?? null,
    weather: input.weather ?? null,
    location: input.location ?? null,
    people: input.people ?? [],
    relationships: input.relationships ?? [],
    tags: input.tags,
  };
}

/** Creates an entry with its tags and returns the new id. */
export async function createEntry(db: Db, input: EntryInput): Promise<string> {
  const id = uuidv7();
  const now = new Date();
  await db.insert(entries).values({
    id,
    entryDate: input.entryDate,
    title: input.title,
    body: input.body,
    mood: input.mood ?? null,
    moodText: input.moodText ?? null,
    weather: input.weather ?? null,
    location: input.location ?? null,
    people: serializeTextList(input.people ?? []),
    relationships: serializeTextList(input.relationships ?? []),
    utcOffsetMinutes: input.utcOffsetMinutes ?? null,
    metadata: serializeMetadata(input.metadata),
    version: 1,
    createdAt: now,
    updatedAt: now,
  });
  await setEntryTags(db, id, input.tags);
  await recordEntryRevision(db, id, 1, revisionSnapshot(input), now);
  return id;
}

/**
 * Updates an entry's content and tags with optimistic concurrency. When
 * `expectedVersion` is provided and no longer matches, the write is rejected as a
 * conflict instead of clobbering a newer copy (web vs mobile). `utcOffsetMinutes`
 * and `metadata` are only touched when present on `input`.
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
    title: input.title,
    body: input.body,
    mood: input.mood ?? null,
    moodText: input.moodText ?? null,
    weather: input.weather ?? null,
    location: input.location ?? null,
    people: serializeTextList(input.people ?? []),
    relationships: serializeTextList(input.relationships ?? []),
    version: nextVersion,
    updatedAt: now,
  };
  if ("utcOffsetMinutes" in input) {
    patch.utcOffsetMinutes = input.utcOffsetMinutes ?? null;
  }
  if ("metadata" in input) {
    patch.metadata = serializeMetadata(input.metadata);
  }

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

  await setEntryTags(db, id, input.tags);
  await recordEntryRevision(db, id, nextVersion, revisionSnapshot(input), now);
  return { status: "updated", version: nextVersion };
}

/**
 * Soft-deletes an entry: sets the tombstone and bumps `updatedAt` so sync clients
 * learn about the removal. The FTS trigger drops it from the keyword index; tag
 * links and attachments are preserved so the delete can be undone.
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

/** Permanently removes an entry and its cascaded rows (tag links, attachments, AI). */
export async function purgeEntry(db: Db, id: string): Promise<void> {
  await db.delete(entries).where(eq(entries.id, id));
}

export async function getEntry(db: Db, id: string): Promise<EntryWithTags | null> {
  const rows = await db
    .select()
    .from(entries)
    .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(and(eq(entries.id, id), isNull(entries.deletedAt)));
  if (rows.length === 0) {
    return null;
  }
  const [composed] = await composeEntries(db, rows);
  return composed ?? null;
}

/** Lists live entries newest-first (by entry date, then creation time) with tags. */
export async function listEntries(db: Db, limit = 50): Promise<EntryWithTags[]> {
  const rows = await db
    .select()
    .from(entries)
    .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(isNull(entries.deletedAt))
    .orderBy(desc(entries.entryDate), desc(entries.createdAt))
    .limit(limit);
  return composeEntries(db, rows);
}

export interface EntryFilter {
  tag?: string;
  person?: string;
  relationship?: string;
  mood?: number;
}

/** Live entry ids carrying a given tag name (via the entry_tags join). */
async function entryIdsForTag(db: Db, name: string): Promise<string[]> {
  const rows = await db
    .select({ id: entryTags.entryId })
    .from(entryTags)
    .innerJoin(tags, eq(entryTags.tagId, tags.id))
    .where(eq(tags.name, name));
  return rows.map((row) => row.id);
}

/**
 * Lists live entries matching an optional facet filter (tag / person /
 * relationship / mood), newest day first. People & relationships match against the
 * stored JSON string arrays (same `"value"` matcher as the topic collector); tags
 * resolve through the entry_tags join. An unknown tag yields an empty result rather
 * than every entry.
 */
export async function listEntriesFiltered(
  db: Db,
  filter: EntryFilter,
  limit = 120,
): Promise<EntryWithTags[]> {
  const conditions: SQL[] = [isNull(entries.deletedAt)];
  if (typeof filter.mood === "number") {
    conditions.push(eq(entries.mood, filter.mood));
  }
  if (filter.person) {
    conditions.push(like(entries.people, `%"${filter.person}"%`));
  }
  if (filter.relationship) {
    conditions.push(like(entries.relationships, `%"${filter.relationship}"%`));
  }
  if (filter.tag) {
    const ids = await entryIdsForTag(db, filter.tag);
    if (ids.length === 0) {
      return [];
    }
    conditions.push(inArray(entries.id, ids));
  }
  const rows = await db
    .select()
    .from(entries)
    .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(and(...conditions))
    .orderBy(desc(entries.entryDate), desc(entries.createdAt))
    .limit(limit);
  return composeEntries(db, rows);
}
