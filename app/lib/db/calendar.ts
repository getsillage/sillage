import { and, desc, eq, gte, lte, ne, sql } from "drizzle-orm";
import type { Db } from "./client";
import type { EntryWithTags } from "./entries";
import { entries } from "./schema";
import { getTagsForEntries } from "./tags";

async function withTags(db: Db, rows: (typeof entries.$inferSelect)[]): Promise<EntryWithTags[]> {
  const tagMap = await getTagsForEntries(
    db,
    rows.map((row) => row.id),
  );
  return rows.map((row) => ({ ...row, tags: tagMap.get(row.id) ?? [] }));
}

/** Counts entries per day within [startDate, endDate] (inclusive). */
export async function getEntryDateCounts(
  db: Db,
  startDate: string,
  endDate: string,
): Promise<Map<string, number>> {
  const rows = await db
    .select({ date: entries.entryDate, count: sql<number>`count(*)` })
    .from(entries)
    .where(and(gte(entries.entryDate, startDate), lte(entries.entryDate, endDate)))
    .groupBy(entries.entryDate);
  return new Map(rows.map((row) => [row.date, Number(row.count)]));
}

/** Lists entries for a single calendar day, newest-created first. */
export async function listEntriesByDate(db: Db, date: string): Promise<EntryWithTags[]> {
  const rows = await db
    .select()
    .from(entries)
    .where(eq(entries.entryDate, date))
    .orderBy(desc(entries.createdAt));
  return withTags(db, rows);
}

/**
 * "On this day": entries from the same month/day (MM-DD) in other years.
 * `today` is a full YYYY-MM-DD string.
 */
export async function getOnThisDay(db: Db, today: string): Promise<EntryWithTags[]> {
  const monthDay = today.slice(5); // MM-DD
  const rows = await db
    .select()
    .from(entries)
    .where(and(sql`substr(${entries.entryDate}, 6) = ${monthDay}`, ne(entries.entryDate, today)))
    .orderBy(desc(entries.entryDate));
  return withTags(db, rows);
}
