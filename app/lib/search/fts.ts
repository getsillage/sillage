import { and, eq, isNull, sql } from "drizzle-orm";
import type { Db } from "~/lib/db/client";
import type { EntryWithTags } from "~/lib/db/entries";
import { entries, entryAi } from "~/lib/db/schema";
import { getTagsForEntries } from "~/lib/db/tags";

export interface SearchResult extends EntryWithTags {
  score: number;
  source: "keyword";
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
 * Soft-deleted entries are already excluded from the index by the FTS triggers;
 * the explicit tombstone filter is defensive belt-and-braces. AI fields are read
 * from the `entry_ai` side table via a left join.
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
      entry: entries,
      ai: entryAi,
      rank: sql<number>`bm25(entries_fts)`,
    })
    .from(entries)
    .innerJoin(sql`entries_fts`, sql`entries_fts.rowid = entries.rowid`)
    .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(and(sql`entries_fts MATCH ${toFtsPhrase(normalized)}`, isNull(entries.deletedAt)))
    .orderBy(sql`bm25(entries_fts)`)
    .limit(limit);

  const tagMap = await getTagsForEntries(
    db,
    rows.map((row) => row.entry.id),
  );

  return rows.map((row) => ({
    ...row.entry,
    summary: row.ai?.summary ?? null,
    sentiment: row.ai?.sentiment ?? null,
    tags: tagMap.get(row.entry.id) ?? [],
    score: Number(row.rank),
    source: "keyword",
  }));
}
