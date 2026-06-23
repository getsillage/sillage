import { eq, inArray } from "drizzle-orm";
import type { Db } from "./client";
import { entryTags, tags } from "./schema";

/** Trims, drops empties, and de-duplicates a list of tag names (case-sensitive). */
export function normalizeTagNames(names: readonly string[]): string[] {
  const seen = new Set<string>();
  for (const raw of names) {
    const name = raw.trim();
    if (name) {
      seen.add(name);
    }
  }
  return [...seen];
}

/** Ensures a tag row exists for each name and returns name -> id. */
async function ensureTags(db: Db, names: readonly string[]): Promise<Map<string, string>> {
  if (names.length === 0) {
    return new Map();
  }
  const rows = names.map((name) => ({ id: crypto.randomUUID(), name }));
  await db.insert(tags).values(rows).onConflictDoNothing();

  const existing = await db
    .select({ id: tags.id, name: tags.name })
    .from(tags)
    .where(inArray(tags.name, [...names]));
  return new Map(existing.map((row) => [row.name, row.id]));
}

/** Replaces the full tag set of an entry with `names`. */
export async function setEntryTags(
  db: Db,
  entryId: string,
  names: readonly string[],
): Promise<void> {
  const normalized = normalizeTagNames(names);
  await db.delete(entryTags).where(eq(entryTags.entryId, entryId));
  if (normalized.length === 0) {
    return;
  }
  const ids = await ensureTags(db, normalized);
  const links = normalized
    .map((name) => ids.get(name))
    .filter((id): id is string => Boolean(id))
    .map((tagId) => ({ entryId, tagId }));
  if (links.length > 0) {
    await db.insert(entryTags).values(links);
  }
}

/** Returns a map of entryId -> sorted tag names for the given entries. */
export async function getTagsForEntries(
  db: Db,
  entryIds: readonly string[],
): Promise<Map<string, string[]>> {
  const result = new Map<string, string[]>();
  if (entryIds.length === 0) {
    return result;
  }
  const rows = await db
    .select({ entryId: entryTags.entryId, name: tags.name })
    .from(entryTags)
    .innerJoin(tags, eq(entryTags.tagId, tags.id))
    .where(inArray(entryTags.entryId, [...entryIds]));

  for (const row of rows) {
    const list = result.get(row.entryId) ?? [];
    list.push(row.name);
    result.set(row.entryId, list);
  }
  for (const list of result.values()) {
    list.sort();
  }
  return result;
}
