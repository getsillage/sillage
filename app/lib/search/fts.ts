import { sql } from "drizzle-orm";
import type { Db } from "~/lib/db/client";
import type { EntryWithTags } from "~/lib/db/entries";
import { entries } from "~/lib/db/schema";
import { getTagsForEntries } from "~/lib/db/tags";

export interface SearchResult extends EntryWithTags {
  score: number;
  source: "keyword" | "semantic";
}

function normalizeQuery(query: string): string {
  return query.trim().replace(/\s+/g, " ");
}

function toFtsPhrase(query: string): string {
  return `"${query.replaceAll('"', '""')}"`;
}

/**
 * Keyword search over D1 FTS5. The migration uses the trigram tokenizer for
 * usable Chinese substring matching, so the raw query can be passed as MATCH.
 */
export async function searchEntriesByKeyword(
  db: Db,
  query: string,
  limit = 20,
): Promise<SearchResult[]> {
  const normalized = normalizeQuery(query);
  if (!normalized) {
    return [];
  }

  const rows = await db
    .select({
      id: entries.id,
      entryDate: entries.entryDate,
      title: entries.title,
      body: entries.body,
      mood: entries.mood,
      weather: entries.weather,
      isPinned: entries.isPinned,
      summary: entries.summary,
      sentiment: entries.sentiment,
      createdAt: entries.createdAt,
      updatedAt: entries.updatedAt,
      rank: sql<number>`bm25(entries_fts)`,
    })
    .from(entries)
    .innerJoin(sql`entries_fts`, sql`entries_fts.rowid = entries.rowid`)
    .where(sql`entries_fts MATCH ${toFtsPhrase(normalized)}`)
    .orderBy(sql`bm25(entries_fts)`)
    .limit(limit);

  const tagMap = await getTagsForEntries(
    db,
    rows.map((row) => row.id),
  );

  return rows.map((row) => ({
    id: row.id,
    entryDate: row.entryDate,
    title: row.title,
    body: row.body,
    mood: row.mood,
    weather: row.weather,
    isPinned: row.isPinned,
    summary: row.summary,
    sentiment: row.sentiment,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
    tags: tagMap.get(row.id) ?? [],
    score: Number(row.rank),
    source: "keyword",
  }));
}
