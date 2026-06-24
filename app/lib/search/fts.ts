import { and, eq, inArray, isNull, like, or, sql } from "drizzle-orm";
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

const SEARCH_STOPWORDS = new Set([
  "我",
  "你",
  "他",
  "她",
  "它",
  "我们",
  "你们",
  "他们",
  "她们",
  "它们",
  "的",
  "了",
  "呢",
  "吗",
  "吧",
  "啊",
  "是",
  "在",
  "有",
  "和",
  "与",
  "及",
  "被",
  "从",
  "到",
  "对",
  "把",
  "根据",
  "记录",
  "指定",
  "系统",
  "不要",
  "请问",
  "问",
  "是什么",
  "什么",
  "今天的",
]);

function extractSearchTerms(query: string): string[] {
  const terms = new Set<string>();
  const segmenter = typeof Intl !== "undefined" && "Segmenter" in Intl
    ? new Intl.Segmenter("zh-Hans", { granularity: "word" })
    : null;

  if (segmenter) {
    for (const segment of segmenter.segment(query)) {
      const text = segment.segment.trim();
      if (!segment.isWordLike || text.length < 2) {
        continue;
      }
      if (SEARCH_STOPWORDS.has(text)) {
        continue;
      }
      terms.add(text);
    }
  }

  if (terms.size === 0) {
    for (const part of query.split(/[\s,，.。!?！？、；;:：()（）【】\[\]{}<>《》"'“”‘’/\\|]+/)) {
      const text = part.trim();
      if (text.length >= 2 && !SEARCH_STOPWORDS.has(text)) {
        terms.add(text);
      }
    }
  }

  return [...terms].slice(0, 6);
}

function getD1Database(db: Db): D1Database {
  const session = (db as unknown as { session?: { client?: D1Database } }).session;
  if (!session?.client) {
    throw new Error("searchEntriesByKeyword requires a D1 database binding");
  }
  return session.client;
}

async function loadEntriesByIds(
  db: Db,
  ids: string[],
): Promise<Array<{ entry: EntryWithTags; score: number }>> {
  if (ids.length === 0) {
    return [];
  }

  const rows = await db
    .select()
    .from(entries)
    .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(inArray(entries.id, ids));

  const rowMap = new Map(rows.map((row) => [row.entries.id, row]));
  const tagMap = await getTagsForEntries(db, ids);

  return ids.flatMap((id) => {
    const row = rowMap.get(id);
    if (!row) {
      return [];
    }
    return [
      {
        entry: {
          ...row.entries,
          summary: row.entry_ai?.summary ?? null,
          sentiment: row.entry_ai?.sentiment ?? null,
          tags: tagMap.get(row.entries.id) ?? [],
        },
        score: 0,
      },
    ];
  });
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

  const ftsRows = await db
    .select({
      id: entries.id,
      rank: sql<number>`bm25(entries_fts)`.as("rank"),
    })
    .from(entries)
    .innerJoin(sql`entries_fts`, sql`entries_fts.rowid = entries.rowid`)
    .where(and(sql`entries_fts MATCH ${toFtsPhrase(normalized)}`, isNull(entries.deletedAt)))
    .orderBy(sql`bm25(entries_fts)`)
    .limit(limit);

  const terms = extractSearchTerms(normalized);
  if (terms.length === 0) {
    return [];
  }
  const patternArgs = terms.flatMap((term) => Array(6).fill(`%${term}%`));
  const conditions = terms
    .map(
      () => `(title LIKE ? OR body LIKE ? OR mood_text LIKE ? OR location LIKE ? OR people LIKE ? OR relationships LIKE ?)`,
    )
    .join(" OR ");
  const fieldQuery = `
    SELECT id
    FROM entries
    WHERE deleted_at IS NULL
      AND (${conditions})
    ORDER BY updated_at DESC, created_at DESC
    LIMIT ?
  `;
  const fieldRows = await getD1Database(db)
    .prepare(fieldQuery)
    .bind(...patternArgs, limit)
    .all<{ id: string }>();

  const seen = new Set<string>();
  const ids: string[] = [];
  const scores = new Map<string, number>();

  for (const row of ftsRows) {
    if (seen.has(row.id)) {
      continue;
    }
    seen.add(row.id);
    ids.push(row.id);
    scores.set(row.id, Number(row.rank));
  }

  for (const row of fieldRows.results) {
    if (seen.has(row.id)) {
      continue;
    }
    seen.add(row.id);
    ids.push(row.id);
    scores.set(row.id, 0);
  }

  const rows = await loadEntriesByIds(db, ids);

  return rows.map((row) => ({
    ...row.entry,
    score: scores.get(row.entry.id) ?? row.score,
    source: "keyword",
  }));
}
