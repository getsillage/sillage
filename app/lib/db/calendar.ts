import { and, desc, eq, gte, isNull, lte, ne, sql } from "drizzle-orm";
import type { Db } from "./client";
import { composeEntries, type EntryWithTags } from "./entries";
import { entries, entryAi } from "./schema";

/** Counts live entries per day within [startDate, endDate] (inclusive). */
export async function getEntryDateCounts(
  db: Db,
  startDate: string,
  endDate: string,
): Promise<Map<string, number>> {
  const rows = await db
    .select({ date: entries.entryDate, count: sql<number>`count(*)` })
    .from(entries)
    .where(
      and(
        gte(entries.entryDate, startDate),
        lte(entries.entryDate, endDate),
        isNull(entries.deletedAt),
      ),
    )
    .groupBy(entries.entryDate);
  return new Map(rows.map((row) => [row.date, Number(row.count)]));
}

/** Lists live entries for a single calendar day, newest-created first. */
export async function listEntriesByDate(db: Db, date: string): Promise<EntryWithTags[]> {
  const rows = await db
    .select()
    .from(entries)
    .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(and(eq(entries.entryDate, date), isNull(entries.deletedAt)))
    .orderBy(desc(entries.createdAt));
  return composeEntries(db, rows);
}

/** Lists live entries within [startDate, endDate] (inclusive), newest day first. */
export async function listEntriesByDateRange(
  db: Db,
  startDate: string,
  endDate: string,
): Promise<EntryWithTags[]> {
  const rows = await db
    .select()
    .from(entries)
    .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(
      and(
        gte(entries.entryDate, startDate),
        lte(entries.entryDate, endDate),
        isNull(entries.deletedAt),
      ),
    )
    .orderBy(desc(entries.entryDate), desc(entries.createdAt));
  return composeEntries(db, rows);
}

/**
 * "On this day": live entries from the same month/day (MM-DD) in other years.
 * `today` is a full YYYY-MM-DD string.
 */
export async function getOnThisDay(db: Db, today: string): Promise<EntryWithTags[]> {
  const monthDay = today.slice(5); // MM-DD
  const rows = await db
    .select()
    .from(entries)
    .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(
      and(
        sql`substr(${entries.entryDate}, 6) = ${monthDay}`,
        ne(entries.entryDate, today),
        isNull(entries.deletedAt),
      ),
    )
    .orderBy(desc(entries.entryDate));
  return composeEntries(db, rows);
}
