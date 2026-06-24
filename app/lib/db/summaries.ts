import { and, desc, eq, gte, inArray, isNull, like, lte, or, type SQL } from "drizzle-orm";
import type {
  SummaryFilter,
  SummaryPeriodType,
  SummaryScope,
  SummaryStyle,
} from "~/lib/product/summary-fields";
import { searchEntriesByKeyword } from "~/lib/search/fts";
import type { Db } from "./client";
import { composeEntries, type EntryWithTags } from "./entries";
import { uuidv7 } from "./id";
import { entries, entryAi, entryTags, type Summary, summaries, tags } from "./schema";

export interface SummaryInput {
  scope: SummaryScope;
  periodType: SummaryPeriodType | null;
  startDate: string;
  endDate: string;
  style: SummaryStyle;
  filter: SummaryFilter | null;
  title: string;
  content: string;
  model: string | null;
  sourceEntryIds: string[];
  trigger?: "manual" | "scheduled";
  generatedAt?: Date;
}

/** A summary row with its JSON columns parsed back into structured values. */
export interface SummaryView extends Omit<Summary, "filter" | "sourceEntryIds"> {
  filter: SummaryFilter | null;
  sourceEntryIds: string[];
}

function parseFilter(raw: string | null): SummaryFilter | null {
  if (!raw) {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    return parsed && typeof parsed === "object" ? (parsed as SummaryFilter) : null;
  } catch {
    return null;
  }
}

function parseIds(raw: string | null): string[] {
  if (!raw) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((id): id is string => typeof id === "string") : [];
  } catch {
    return [];
  }
}

export function toSummaryView(row: Summary): SummaryView {
  return {
    ...row,
    filter: parseFilter(row.filter),
    sourceEntryIds: parseIds(row.sourceEntryIds),
  };
}

/** Creates a summary row and returns its new id. */
export async function createSummary(db: Db, input: SummaryInput): Promise<string> {
  const id = uuidv7();
  const now = new Date();
  await db.insert(summaries).values({
    id,
    scope: input.scope,
    periodType: input.periodType,
    startDate: input.startDate,
    endDate: input.endDate,
    style: input.style,
    filter: input.filter ? JSON.stringify(input.filter) : null,
    title: input.title,
    content: input.content,
    model: input.model,
    sourceEntryIds: JSON.stringify(input.sourceEntryIds),
    trigger: input.trigger ?? "manual",
    generatedAt: input.generatedAt ?? now,
    createdAt: now,
    updatedAt: now,
  });
  return id;
}

export interface SummaryContentPatch {
  title: string;
  content: string;
  model: string | null;
  style: SummaryStyle;
  startDate: string;
  endDate: string;
  sourceEntryIds: string[];
  generatedAt?: Date;
}

/** Overwrites an existing summary's generated content (used for "regenerate"). */
export async function updateSummary(db: Db, id: string, patch: SummaryContentPatch): Promise<void> {
  const now = new Date();
  await db
    .update(summaries)
    .set({
      title: patch.title,
      content: patch.content,
      model: patch.model,
      style: patch.style,
      startDate: patch.startDate,
      endDate: patch.endDate,
      sourceEntryIds: JSON.stringify(patch.sourceEntryIds),
      generatedAt: patch.generatedAt ?? now,
      updatedAt: now,
    })
    .where(and(eq(summaries.id, id), isNull(summaries.deletedAt)));
}

export async function getSummary(db: Db, id: string): Promise<SummaryView | null> {
  const [row] = await db
    .select()
    .from(summaries)
    .where(and(eq(summaries.id, id), isNull(summaries.deletedAt)));
  return row ? toSummaryView(row) : null;
}

/** Finds a live period summary for the exact window (dedup / regenerate target). */
export async function findPeriodSummary(
  db: Db,
  periodType: SummaryPeriodType,
  startDate: string,
  endDate: string,
): Promise<SummaryView | null> {
  const [row] = await db
    .select()
    .from(summaries)
    .where(
      and(
        eq(summaries.scope, "period"),
        eq(summaries.periodType, periodType),
        eq(summaries.startDate, startDate),
        eq(summaries.endDate, endDate),
        isNull(summaries.deletedAt),
      ),
    );
  return row ? toSummaryView(row) : null;
}

export async function listSummaries(
  db: Db,
  options: { scope?: SummaryScope; limit?: number } = {},
): Promise<SummaryView[]> {
  const conditions: SQL[] = [isNull(summaries.deletedAt)];
  if (options.scope) {
    conditions.push(eq(summaries.scope, options.scope));
  }
  const rows = await db
    .select()
    .from(summaries)
    .where(and(...conditions))
    .orderBy(desc(summaries.generatedAt))
    .limit(options.limit ?? 30);
  return rows.map(toSummaryView);
}

/** Soft-deletes a summary; bumps updatedAt so sync clients learn of the removal. */
export async function deleteSummary(db: Db, id: string): Promise<void> {
  const now = new Date();
  await db
    .update(summaries)
    .set({ deletedAt: now, updatedAt: now })
    .where(and(eq(summaries.id, id), isNull(summaries.deletedAt)));
}

function cleanValues(values: readonly string[] | undefined): string[] {
  return (values ?? []).map((value) => value.trim()).filter(Boolean);
}

/** Live entry ids that carry any of the given tag names. */
async function idsByTags(db: Db, names: readonly string[]): Promise<string[]> {
  const values = cleanValues(names);
  if (values.length === 0) {
    return [];
  }
  const rows = await db
    .select({ id: entryTags.entryId })
    .from(entryTags)
    .innerJoin(tags, eq(entryTags.tagId, tags.id))
    .innerJoin(entries, eq(entries.id, entryTags.entryId))
    .where(and(inArray(tags.name, values), isNull(entries.deletedAt)));
  return rows.map((row) => row.id);
}

/** Live entry ids whose JSON string-array column contains any of the values. */
async function idsByJsonArray(
  db: Db,
  column: typeof entries.people | typeof entries.relationships,
  values: readonly string[],
): Promise<string[]> {
  const cleaned = cleanValues(values);
  if (cleaned.length === 0) {
    return [];
  }
  const matchers = cleaned.map((value) => like(column, `%"${value}"%`));
  const rows = await db
    .select({ id: entries.id })
    .from(entries)
    .where(and(isNull(entries.deletedAt), or(...matchers)));
  return rows.map((row) => row.id);
}

/**
 * Resolves a topic filter (tags / people / relationships / keyword / hand-picked
 * ids) into a de-duplicated, live entry set, optionally constrained to a date
 * window. Returns entries newest day first. Empty filter → empty result.
 */
export async function collectEntriesForTopic(
  db: Db,
  filter: SummaryFilter,
  window?: { startDate: string; endDate: string },
): Promise<EntryWithTags[]> {
  const idSet = new Set<string>();

  for (const id of await idsByTags(db, filter.tags ?? [])) {
    idSet.add(id);
  }
  for (const id of await idsByJsonArray(db, entries.people, filter.people ?? [])) {
    idSet.add(id);
  }
  for (const id of await idsByJsonArray(db, entries.relationships, filter.relationships ?? [])) {
    idSet.add(id);
  }
  const keyword = filter.keyword?.trim();
  if (keyword) {
    for (const result of await searchEntriesByKeyword(db, keyword, 100)) {
      idSet.add(result.id);
    }
  }
  for (const id of cleanValues(filter.entryIds)) {
    idSet.add(id);
  }

  const ids = [...idSet];
  if (ids.length === 0) {
    return [];
  }

  const conditions: SQL[] = [inArray(entries.id, ids), isNull(entries.deletedAt)];
  if (window) {
    conditions.push(gte(entries.entryDate, window.startDate));
    conditions.push(lte(entries.entryDate, window.endDate));
  }
  const rows = await db
    .select()
    .from(entries)
    .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(and(...conditions))
    .orderBy(desc(entries.entryDate), desc(entries.createdAt));
  return composeEntries(db, rows);
}
