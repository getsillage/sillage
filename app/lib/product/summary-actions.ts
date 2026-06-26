import { env } from "cloudflare:workers";
import { generateSummary } from "~/lib/ai/summarize";
import { rangeForPeriod, todayISO } from "~/lib/date";
import { listAllEntriesByDate, listEntriesByDateRange } from "~/lib/db/calendar";
import type { Db } from "~/lib/db/client";
import {
  collectEntriesForTopic,
  createSummary,
  deleteSummary,
  findLatestPeriodSummary,
  findPeriodSummary,
  getSummary,
  updateSummary,
} from "~/lib/db/summaries";
import {
  isSummaryPeriodType,
  isSummaryStyle,
  type SummaryFilter,
  type SummaryPeriodType,
} from "~/lib/product/summary-fields";
import {
  type SummaryGenerateInput,
  summaryGenerateFromData,
  summaryGenerateSchema,
} from "~/lib/validation/summary";

export type SummaryIntent = "generate" | "delete" | "regenerate-summary";

export interface SummaryActionData {
  intent: SummaryIntent;
  ok: boolean;
  message: string;
}

export const SUMMARY_INTENTS: readonly SummaryIntent[] = [
  "generate",
  "delete",
  "regenerate-summary",
];

export function isSummaryIntent(value: string): value is SummaryIntent {
  return (SUMMARY_INTENTS as readonly string[]).includes(value);
}

function friendlyReason(reason?: string): string {
  if (!reason) {
    return "未能生成总结";
  }
  if (reason.includes("disabled")) {
    return "AI 未启用，请先在「设置」中配置并启用 AI 提供商";
  }
  if (reason.includes("key not configured")) {
    return "尚未配置 API Key，请到「设置」补全";
  }
  if (reason.includes("没有记录")) {
    return reason;
  }
  return `未能生成：${reason}`;
}

function topicLabelFromFilter(filter: SummaryFilter): string {
  const parts: string[] = [];
  if (filter.tags?.length) {
    parts.push(filter.tags.map((tag) => `#${tag}`).join(" "));
  }
  if (filter.people?.length) {
    parts.push(`人物：${filter.people.join("、")}`);
  }
  if (filter.relationships?.length) {
    parts.push(`关系：${filter.relationships.join("、")}`);
  }
  if (filter.keyword) {
    parts.push(`关键词「${filter.keyword}」`);
  }
  if (filter.entryIds?.length) {
    parts.push(`手选 ${filter.entryIds.length} 条`);
  }
  return parts.join(" · ");
}

function topicFilterFromInput(input: SummaryGenerateInput): SummaryFilter {
  const filter: SummaryFilter = {};
  if (input.filter.keyword) filter.keyword = input.filter.keyword;
  return filter;
}

function periodWindowFromInput(
  input: SummaryGenerateInput,
  today: string,
): {
  periodType: SummaryPeriodType;
  window: { startDate: string; endDate: string } | null;
} {
  const periodType = input.periodType as SummaryPeriodType;
  if (periodType === "all") {
    return { periodType, window: null };
  }
  const window =
    periodType === "custom" && input.startDate && input.endDate
      ? { startDate: input.startDate, endDate: input.endDate }
      : rangeForPeriod(periodType, today);
  return { periodType, window };
}

function rangeFromEntries(
  entries: { entryDate: string }[],
  fallback: { startDate: string; endDate: string },
): { startDate: string; endDate: string } {
  if (entries.length === 0) {
    return fallback;
  }
  return { startDate: entries[entries.length - 1].entryDate, endDate: entries[0].entryDate };
}

/** Runs summary intents from 问答: generate / delete / regenerate a multi-entry summary. */
export async function runSummaryAction(
  db: Db,
  form: FormData,
  intent: SummaryIntent,
): Promise<SummaryActionData> {
  if (intent === "delete") {
    const id = String(form.get("id") ?? "").trim();
    if (id) {
      await deleteSummary(db, id);
    }
    return { intent: "delete", ok: true, message: "已删除总结" };
  }

  if (intent === "regenerate-summary") {
    const id = String(form.get("id") ?? "").trim();
    const existing = id ? await getSummary(db, id) : null;
    if (!existing) {
      return { intent: "regenerate-summary", ok: false, message: "总结不存在" };
    }
    const style = isSummaryStyle(existing.style) ? existing.style : "brief";
    const periodType =
      existing.periodType && isSummaryPeriodType(existing.periodType) ? existing.periodType : null;
    const existingWindow =
      periodType && periodType !== "all"
        ? { startDate: existing.startDate, endDate: existing.endDate }
        : undefined;
    const entries =
      existing.scope === "topic"
        ? await collectEntriesForTopic(db, existing.filter ?? {}, existingWindow)
        : periodType === "all"
          ? await listAllEntriesByDate(db)
          : await listEntriesByDateRange(db, existing.startDate, existing.endDate);
    const storedWindow = { startDate: existing.startDate, endDate: existing.endDate };
    const range =
      existing.scope === "topic" && (!periodType || periodType === "all")
        ? rangeFromEntries(entries, storedWindow)
        : periodType === "all"
          ? rangeFromEntries(entries, storedWindow)
          : storedWindow;
    const draft = await generateSummary(env, {
      scope: existing.scope === "topic" ? "topic" : "period",
      periodType,
      startDate: range.startDate,
      endDate: range.endDate,
      style,
      entries,
      topicLabel: existing.filter ? topicLabelFromFilter(existing.filter) : undefined,
    });
    if (!draft.ok) {
      return {
        intent: "regenerate-summary",
        ok: false,
        message: friendlyReason(draft.skippedReason),
      };
    }
    await updateSummary(db, existing.id, {
      title: draft.title,
      content: draft.content,
      model: draft.model,
      style,
      startDate: range.startDate,
      endDate: range.endDate,
      sourceEntryIds: entries.map((entry) => entry.id),
    });
    return { intent: "regenerate-summary", ok: true, message: "已重新生成总结" };
  }

  // intent === "generate"
  const parsed = summaryGenerateSchema.safeParse(summaryGenerateFromData(form));
  if (!parsed.success) {
    return {
      intent: "generate",
      ok: false,
      message: parsed.error.issues[0]?.message ?? "输入有误",
    };
  }
  const input = parsed.data;
  const today = todayISO();

  if (!input.useTopic) {
    const { periodType, window } = periodWindowFromInput(input, today);
    const entries = window
      ? await listEntriesByDateRange(db, window.startDate, window.endDate)
      : await listAllEntriesByDate(db);
    const range = window ?? rangeFromEntries(entries, { startDate: today, endDate: today });
    const draft = await generateSummary(env, {
      scope: "period",
      periodType,
      startDate: range.startDate,
      endDate: range.endDate,
      style: input.style,
      entries,
    });
    if (!draft.ok) {
      return { intent: "generate", ok: false, message: friendlyReason(draft.skippedReason) };
    }
    const existing =
      periodType === "all"
        ? await findLatestPeriodSummary(db, periodType)
        : await findPeriodSummary(db, periodType, range.startDate, range.endDate);
    if (existing) {
      await updateSummary(db, existing.id, {
        title: draft.title,
        content: draft.content,
        model: draft.model,
        style: input.style,
        startDate: range.startDate,
        endDate: range.endDate,
        sourceEntryIds: entries.map((entry) => entry.id),
      });
    } else {
      await createSummary(db, {
        scope: "period",
        periodType,
        startDate: range.startDate,
        endDate: range.endDate,
        style: input.style,
        filter: null,
        title: draft.title,
        content: draft.content,
        model: draft.model,
        sourceEntryIds: entries.map((entry) => entry.id),
      });
    }
    return { intent: "generate", ok: true, message: "已生成总结" };
  }

  const filter = topicFilterFromInput(input);
  const period = input.usePeriod ? periodWindowFromInput(input, today) : null;
  const entries = await collectEntriesForTopic(db, filter, period?.window ?? undefined);
  const range = period?.window ?? rangeFromEntries(entries, { startDate: today, endDate: today });
  const draft = await generateSummary(env, {
    scope: "topic",
    periodType: period?.periodType ?? null,
    startDate: range.startDate,
    endDate: range.endDate,
    style: input.style,
    entries,
    topicLabel: topicLabelFromFilter(filter),
  });
  if (!draft.ok) {
    return { intent: "generate", ok: false, message: friendlyReason(draft.skippedReason) };
  }
  await createSummary(db, {
    scope: "topic",
    periodType: period?.periodType ?? null,
    startDate: range.startDate,
    endDate: range.endDate,
    style: input.style,
    filter,
    title: draft.title,
    content: draft.content,
    model: draft.model,
    sourceEntryIds: entries.map((entry) => entry.id),
  });
  return { intent: "generate", ok: true, message: "已生成总结" };
}
