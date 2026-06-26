import { and, desc, eq, inArray, isNotNull, isNull, like, or, type SQL } from "drizzle-orm";
import type { Db } from "~/lib/db/client";
import { composeEntries, type EntryWithTags } from "~/lib/db/entries";
import { entries, entryAi, summaries } from "~/lib/db/schema";
import { listSummaries, type SummaryView, toSummaryView } from "~/lib/db/summaries";
import { entryKindLabel, normalizeEntryKind, parseTextList } from "~/lib/product/entry-fields";
import {
  isSummaryPeriodType,
  isSummaryStyle,
  PERIOD_TYPE_LABELS,
  STYLE_LABELS,
} from "~/lib/product/summary-fields";
import { extractSearchTerms, searchEntriesByKeyword } from "~/lib/search/fts";

export const ASK_SOURCE_TYPES = ["fragment", "note", "draft", "entry-ai", "summary"] as const;

export type AskSourceType = (typeof ASK_SOURCE_TYPES)[number];

export const DEFAULT_ASK_SOURCE_TYPES: AskSourceType[] = ["fragment", "note"];

export interface AskCitation {
  id: string;
  title: string;
  label: string;
  href: string;
  kind: "entry" | "summary";
}

export interface AskContext {
  entries: EntryWithTags[];
  summaries: SummaryView[];
  evidence: string;
  citations: AskCitation[];
}

const ENTRY_LIMIT = 60;
const SEARCH_LIMIT = 24;
const RECENT_LIMIT = 80;
const SUMMARY_LIMIT = 12;
const MAX_BODY_CHARS = 700;
const MAX_AI_SUMMARY_CHARS = 400;
const MAX_REVIEW_CHARS = 900;

function uniqueSourceTypes(values: Iterable<unknown>): AskSourceType[] {
  const selected = new Set<AskSourceType>();
  for (const value of values) {
    if (typeof value === "string" && ASK_SOURCE_TYPES.includes(value as AskSourceType)) {
      selected.add(value as AskSourceType);
    }
  }
  return selected.size > 0 ? [...selected] : [...DEFAULT_ASK_SOURCE_TYPES];
}

export function askSourceTypesFromForm(form: FormData): AskSourceType[] {
  return uniqueSourceTypes(form.getAll("sources"));
}

function truncate(text: string | null | undefined, limit: number): string {
  const trimmed = (text ?? "").trim();
  return trimmed.length > limit ? `${trimmed.slice(0, limit)}...` : trimmed;
}

function entryMatchesSelectedKind(entry: EntryWithTags, selected: Set<AskSourceType>): boolean {
  const kind = normalizeEntryKind(entry.kind);
  return (
    (kind === "fragment" && selected.has("fragment")) ||
    (kind === "note" && selected.has("note")) ||
    (kind === "draft" && selected.has("draft"))
  );
}

function entryCanContributeEvidence(entry: EntryWithTags, selected: Set<AskSourceType>): boolean {
  return (
    entryMatchesSelectedKind(entry, selected) ||
    (selected.has("entry-ai") && Boolean(entry.summary))
  );
}

function dedupeEvidenceEntries(rows: EntryWithTags[], selected: Set<AskSourceType>, limit: number) {
  const seen = new Set<string>();
  const result: EntryWithTags[] = [];
  for (const entry of rows) {
    if (seen.has(entry.id) || !entryCanContributeEvidence(entry, selected)) {
      continue;
    }
    seen.add(entry.id);
    result.push(entry);
    if (result.length >= limit) {
      break;
    }
  }
  return result;
}

async function listRecentEntriesByKinds(
  db: Db,
  selected: Set<AskSourceType>,
  limit: number,
): Promise<EntryWithTags[]> {
  const kinds = (["fragment", "note", "draft"] as const).filter((kind) => selected.has(kind));
  if (kinds.length === 0) {
    return [];
  }
  const rows = await db
    .select()
    .from(entries)
    .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(and(isNull(entries.deletedAt), inArray(entries.kind, kinds)))
    .orderBy(desc(entries.entryDate), desc(entries.createdAt))
    .limit(limit);
  return composeEntries(db, rows);
}

async function searchEntriesWithAiSummaries(
  db: Db,
  question: string,
  limit: number,
): Promise<EntryWithTags[]> {
  const terms = extractSearchTerms(question);
  if (terms.length === 0) {
    return [];
  }
  const rows = await db
    .select()
    .from(entries)
    .innerJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(
      and(
        isNull(entries.deletedAt),
        isNotNull(entryAi.summary),
        or(...terms.map((term) => like(entryAi.summary, `%${term}%`))),
      ),
    )
    .orderBy(desc(entryAi.generatedAt), desc(entries.entryDate), desc(entries.createdAt))
    .limit(limit);
  return composeEntries(db, rows);
}

async function listEntriesWithAiSummaries(db: Db, limit: number): Promise<EntryWithTags[]> {
  const rows = await db
    .select()
    .from(entries)
    .innerJoin(entryAi, eq(entryAi.entryId, entries.id))
    .where(and(isNull(entries.deletedAt), isNotNull(entryAi.summary)))
    .orderBy(desc(entryAi.generatedAt), desc(entries.entryDate), desc(entries.createdAt))
    .limit(limit);
  return composeEntries(db, rows);
}

function likeConditions(
  columns: Array<typeof summaries.title | typeof summaries.content>,
  terms: string[],
): SQL[] {
  return terms.map((term) => or(...columns.map((column) => like(column, `%${term}%`))) as SQL);
}

async function searchSummariesByQuestion(db: Db, question: string, limit: number) {
  const terms = extractSearchTerms(question);
  if (terms.length === 0) {
    return [];
  }
  const rows = await db
    .select()
    .from(summaries)
    .where(
      and(
        isNull(summaries.deletedAt),
        or(...likeConditions([summaries.title, summaries.content], terms)),
      ),
    )
    .orderBy(desc(summaries.generatedAt))
    .limit(limit);
  return rows.map(toSummaryView);
}

function dedupeSummaries(rows: SummaryView[], limit: number): SummaryView[] {
  const seen = new Set<string>();
  const result: SummaryView[] = [];
  for (const row of rows) {
    if (seen.has(row.id)) {
      continue;
    }
    seen.add(row.id);
    result.push(row);
    if (result.length >= limit) {
      break;
    }
  }
  return result;
}

function entryTitle(entry: EntryWithTags): string {
  return entry.title.trim() || "(无标题)";
}

function entryEvidenceBlock(entry: EntryWithTags, selected: Set<AskSourceType>): string {
  const includeRawEntry = entryMatchesSelectedKind(entry, selected);
  const includeAiSummary = selected.has("entry-ai");
  const selectedKind = normalizeEntryKind(entry.kind);
  const people = parseTextList(entry.people);
  const relationships = parseTextList(entry.relationships);
  return [
    `【${entry.entryDate} · ${entryKindLabel(selectedKind)}】${entryTitle(entry)}`,
    includeRawEntry && entry.moodText ? `心情：${entry.moodText}` : "",
    includeRawEntry && entry.location ? `地点：${entry.location}` : "",
    includeRawEntry && people.length > 0 ? `人物：${people.join("、")}` : "",
    includeRawEntry && relationships.length > 0 ? `关系：${relationships.join("、")}` : "",
    includeRawEntry && entry.tags.length > 0
      ? `标签：${entry.tags.map((tag) => `#${tag}`).join(" ")}`
      : "",
    includeRawEntry ? truncate(entry.body, MAX_BODY_CHARS) : "",
    includeAiSummary && entry.summary
      ? `AI 洞察：${truncate(entry.summary, MAX_AI_SUMMARY_CHARS)}`
      : "",
  ]
    .filter(Boolean)
    .join("\n");
}

function summaryLabel(summary: SummaryView): string {
  const period =
    summary.periodType && isSummaryPeriodType(summary.periodType)
      ? PERIOD_TYPE_LABELS[summary.periodType]
      : null;
  if (summary.scope === "topic") {
    return period ? `主题总结 · ${period}` : "主题总结";
  }
  const style = isSummaryStyle(summary.style) ? STYLE_LABELS[summary.style] : summary.style;
  return `${period ?? "时间范围"} · ${style}`;
}

function summaryEvidenceBlock(summary: SummaryView): string {
  const range =
    summary.startDate === summary.endDate
      ? summary.startDate
      : `${summary.startDate} 至 ${summary.endDate}`;
  return [
    `【AI 总结 · ${summaryLabel(summary)} · ${range}】${summary.title || "未命名总结"}`,
    truncate(summary.content, MAX_REVIEW_CHARS),
    summary.sourceEntryIds.length > 0 ? `原始来源数量：${summary.sourceEntryIds.length}` : "",
  ]
    .filter(Boolean)
    .join("\n");
}

function buildEvidence(
  entriesForEvidence: EntryWithTags[],
  summariesForEvidence: SummaryView[],
  selected: Set<AskSourceType>,
): string {
  const blocks = [
    ...entriesForEvidence.map((entry) => entryEvidenceBlock(entry, selected)),
    ...summariesForEvidence.map(summaryEvidenceBlock),
  ];
  return blocks.join("\n\n---\n\n");
}

function entryCitation(entry: EntryWithTags): AskCitation {
  return {
    id: entry.id,
    title: entryTitle(entry),
    label: `${entry.entryDate} · ${entryTitle(entry)}`,
    href: `/entries/${entry.id}`,
    kind: "entry",
  };
}

function dedupeCitationEntries(
  rows: EntryWithTags[],
  selected: Set<AskSourceType>,
  limit: number,
): EntryWithTags[] {
  const seen = new Set<string>();
  const result: EntryWithTags[] = [];
  for (const entry of rows) {
    if (seen.has(entry.id) || !entryCanContributeEvidence(entry, selected)) {
      continue;
    }
    seen.add(entry.id);
    result.push(entry);
    if (result.length >= limit) {
      break;
    }
  }
  return result;
}

function summaryCitation(summary: SummaryView): AskCitation {
  return {
    id: summary.id,
    title: summary.title || "未命名总结",
    label: `AI 总结 · ${summary.title || summaryLabel(summary)}`,
    href: `/ask#summary-${summary.id}`,
    kind: "summary",
  };
}

export async function collectAskContext(
  db: Db,
  question: string,
  sourceTypes: AskSourceType[] = DEFAULT_ASK_SOURCE_TYPES,
): Promise<AskContext> {
  const selected = new Set(uniqueSourceTypes(sourceTypes));
  const includeEntries = selected.has("fragment") || selected.has("note") || selected.has("draft");
  const includeAiEntrySummaries = selected.has("entry-ai");
  const includeSummaries = selected.has("summary");

  const [
    matchedEntries,
    recentEntries,
    matchedAiEntries,
    recentAiEntries,
    matchedSummaries,
    recentSummaries,
  ] = await Promise.all([
    includeEntries ? searchEntriesByKeyword(db, question, SEARCH_LIMIT) : Promise.resolve([]),
    includeEntries ? listRecentEntriesByKinds(db, selected, RECENT_LIMIT) : Promise.resolve([]),
    includeAiEntrySummaries
      ? searchEntriesWithAiSummaries(db, question, SEARCH_LIMIT)
      : Promise.resolve([]),
    includeAiEntrySummaries ? listEntriesWithAiSummaries(db, 30) : Promise.resolve([]),
    includeSummaries ? searchSummariesByQuestion(db, question, SUMMARY_LIMIT) : Promise.resolve([]),
    includeSummaries ? listSummaries(db, { limit: SUMMARY_LIMIT }) : Promise.resolve([]),
  ]);

  const entriesForEvidence = dedupeEvidenceEntries(
    [...matchedEntries, ...recentEntries, ...matchedAiEntries, ...recentAiEntries],
    selected,
    ENTRY_LIMIT,
  );
  const summariesForEvidence = includeSummaries
    ? dedupeSummaries([...matchedSummaries, ...recentSummaries], SUMMARY_LIMIT)
    : [];
  const citedEntries = dedupeCitationEntries(
    [...matchedEntries, ...entriesForEvidence],
    selected,
    includeSummaries ? 4 : 6,
  );
  const citedSummaries = includeSummaries ? summariesForEvidence.slice(0, 3) : [];

  return {
    entries: entriesForEvidence,
    summaries: summariesForEvidence,
    evidence: buildEvidence(entriesForEvidence, summariesForEvidence, selected),
    citations: [...citedEntries.map(entryCitation), ...citedSummaries.map(summaryCitation)],
  };
}
