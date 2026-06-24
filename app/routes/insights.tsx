import { env } from "cloudflare:workers";
import { useState } from "react";
import { Link, useFetcher } from "react-router";
import { Markdown } from "~/components/Markdown";
import { SuggestedInput } from "~/components/SuggestedInput";
import {
  helperTextClass,
  inputClass,
  labelClass,
  pageLeadClass,
  pageSectionClass,
  pageShellClass,
  pageTitleClass,
  panelClass,
  primaryButtonClass,
  subtleButtonClass,
  subtlePanelClass,
} from "~/components/ui";
import { runAiPipeline } from "~/lib/ai/pipeline";
import { generateSummary } from "~/lib/ai/summarize";
import { requireSession } from "~/lib/auth/session";
import { rangeForPeriod, todayISO, toISODate } from "~/lib/date";
import { listEntriesByDate, listEntriesByDateRange } from "~/lib/db/calendar";
import { getDb } from "~/lib/db/client";
import { getEntry, listEntries } from "~/lib/db/entries";
import {
  collectEntriesForTopic,
  createSummary,
  deleteSummary,
  findPeriodSummary,
  getSummary,
  listSummaries,
  updateSummary,
} from "~/lib/db/summaries";
import { normalizeEntryKind } from "~/lib/product/entry-fields";
import { buildEntryFormSuggestions } from "~/lib/product/entry-suggestions";
import {
  isSummaryPeriodType,
  isSummaryStyle,
  PERIOD_TYPE_LABELS,
  STYLE_LABELS,
  SUMMARY_PERIOD_TYPES,
  SUMMARY_STYLES,
  type SummaryFilter,
  type SummaryPeriodType,
  type SummaryStyle,
} from "~/lib/product/summary-fields";
import { summaryGenerateFromData, summaryGenerateSchema } from "~/lib/validation/summary";
import type { Route } from "./+types/insights";

type SummaryActionData = {
  intent: "generate" | "delete" | "regenerate-summary" | "regenerate-entry";
  ok: boolean;
  message: string;
};

export function meta(_: Route.MetaArgs) {
  return [{ title: "洞察 · Sillage" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const today = todayISO();
  const [todayEntries, recentEntries, summaryRows] = await Promise.all([
    listEntriesByDate(db, today),
    listEntries(db, 80),
    listSummaries(db, { limit: 30 }),
  ]);
  const suggestions = buildEntryFormSuggestions(recentEntries);
  return {
    today,
    todayInsights: todayEntries.filter((entry) => entry.summary),
    recentInsights: recentEntries.filter((entry) => entry.summary).slice(0, 12),
    themes: recentEntries
      .flatMap((entry) => entry.tags)
      .reduce<Record<string, number>>((acc, tag) => {
        acc[tag] = (acc[tag] ?? 0) + 1;
        return acc;
      }, {}),
    noteCount: recentEntries.filter((entry) => normalizeEntryKind(entry.kind) === "note").length,
    suggestions,
    pickerEntries: recentEntries
      .slice(0, 40)
      .map((entry) => ({ id: entry.id, entryDate: entry.entryDate, title: entry.title })),
    summaries: summaryRows.map((row) => ({
      id: row.id,
      scope: row.scope,
      periodType: row.periodType,
      startDate: row.startDate,
      endDate: row.endDate,
      style: row.style,
      title: row.title,
      content: row.content,
      sourceEntryIds: row.sourceEntryIds,
      generatedAt: toISODate(new Date(row.generatedAt)),
    })),
  };
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

function rangeFromEntries(
  entries: { entryDate: string }[],
  fallback: { startDate: string; endDate: string },
): { startDate: string; endDate: string } {
  if (entries.length === 0) {
    return fallback;
  }
  return { startDate: entries[entries.length - 1].entryDate, endDate: entries[0].entryDate };
}

export async function action({ request }: Route.ActionArgs): Promise<SummaryActionData> {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const form = await request.formData();
  const intent = String(form.get("intent") ?? "generate");
  const allowedIntents = ["generate", "delete", "regenerate-summary", "regenerate-entry"];

  if (!allowedIntents.includes(intent)) {
    return { intent: "generate", ok: false, message: "不支持的操作" };
  }

  if (intent === "delete") {
    const id = String(form.get("id") ?? "").trim();
    if (id) {
      await deleteSummary(db, id);
    }
    return { intent: "delete", ok: true, message: "已删除总结" };
  }

  if (intent === "regenerate-entry") {
    const entryId = String(form.get("entryId") ?? "").trim();
    const entry = entryId ? await getEntry(db, entryId) : null;
    if (!entry) {
      return { intent: "regenerate-entry", ok: false, message: "记录不存在" };
    }
    const result = await runAiPipeline(env, entry);
    return result.summaryUpdated
      ? { intent: "regenerate-entry", ok: true, message: "已重新生成摘要" }
      : {
          intent: "regenerate-entry",
          ok: false,
          message: friendlyReason(result.skippedReasons[0]),
        };
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
    const entries =
      existing.scope === "topic"
        ? await collectEntriesForTopic(db, existing.filter ?? {})
        : await listEntriesByDateRange(db, existing.startDate, existing.endDate);
    const range = rangeFromEntries(entries, {
      startDate: existing.startDate,
      endDate: existing.endDate,
    });
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

  if (input.scope === "period") {
    const periodType = input.periodType as SummaryPeriodType;
    const window =
      periodType === "custom" && input.startDate && input.endDate
        ? { startDate: input.startDate, endDate: input.endDate }
        : rangeForPeriod(periodType, today);
    const entries = await listEntriesByDateRange(db, window.startDate, window.endDate);
    const draft = await generateSummary(env, {
      scope: "period",
      periodType,
      startDate: window.startDate,
      endDate: window.endDate,
      style: input.style,
      entries,
    });
    if (!draft.ok) {
      return { intent: "generate", ok: false, message: friendlyReason(draft.skippedReason) };
    }
    const existing = await findPeriodSummary(db, periodType, window.startDate, window.endDate);
    if (existing) {
      await updateSummary(db, existing.id, {
        title: draft.title,
        content: draft.content,
        model: draft.model,
        style: input.style,
        startDate: window.startDate,
        endDate: window.endDate,
        sourceEntryIds: entries.map((entry) => entry.id),
      });
    } else {
      await createSummary(db, {
        scope: "period",
        periodType,
        startDate: window.startDate,
        endDate: window.endDate,
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

  // scope === "topic"
  const filter: SummaryFilter = {};
  if (input.filter.tags.length) filter.tags = input.filter.tags;
  if (input.filter.people.length) filter.people = input.filter.people;
  if (input.filter.relationships.length) filter.relationships = input.filter.relationships;
  if (input.filter.keyword) filter.keyword = input.filter.keyword;
  if (input.filter.entryIds.length) filter.entryIds = input.filter.entryIds;

  const entries = await collectEntriesForTopic(db, filter);
  const range = rangeFromEntries(entries, { startDate: today, endDate: today });
  const draft = await generateSummary(env, {
    scope: "topic",
    periodType: null,
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
    periodType: null,
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

type LoadedSummary = Route.ComponentProps["loaderData"]["summaries"][number];

function chipClass(active: boolean): string {
  return active
    ? "rounded-full border border-gray-950 bg-gray-950 px-3 py-1.5 text-sm text-white dark:border-gray-100 dark:bg-gray-100 dark:text-gray-950"
    : "rounded-full border border-gray-200 bg-white px-3 py-1.5 text-gray-700 text-sm transition hover:border-gray-300 hover:bg-gray-50 dark:border-gray-800 dark:bg-gray-950 dark:text-gray-300 dark:hover:border-gray-700 dark:hover:bg-gray-900";
}

function badgeClass(): string {
  return "rounded-full bg-gray-100 px-2 py-0.5 text-gray-600 text-xs dark:bg-gray-800 dark:text-gray-300";
}

function statusClass(ok: boolean): string {
  return `rounded-lg border px-3 py-2 text-sm ${
    ok
      ? "border-green-300 bg-green-50 text-green-800 dark:border-green-900/70 dark:bg-green-950/50 dark:text-green-200"
      : "border-red-300 bg-red-50 text-red-800 dark:border-red-900/70 dark:bg-red-950/50 dark:text-red-200"
  }`;
}

function ChipGroup({
  options,
  value,
  onChange,
}: {
  options: ReadonlyArray<{ value: string; label: string }>;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="mt-2 flex flex-wrap gap-2">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          className={chipClass(value === option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

const STYLE_OPTIONS = SUMMARY_STYLES.map((style) => ({ value: style, label: STYLE_LABELS[style] }));
const PERIOD_OPTIONS = SUMMARY_PERIOD_TYPES.map((period) => ({
  value: period,
  label: PERIOD_TYPE_LABELS[period],
}));

function GeneratePanel({
  suggestions,
  pickerEntries,
}: {
  suggestions: Route.ComponentProps["loaderData"]["suggestions"];
  pickerEntries: Route.ComponentProps["loaderData"]["pickerEntries"];
}) {
  const fetcher = useFetcher<SummaryActionData>();
  const [scope, setScope] = useState<"period" | "topic">("period");
  const [periodType, setPeriodType] = useState<SummaryPeriodType>("week");
  const [style, setStyle] = useState<SummaryStyle>("brief");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const generating = fetcher.state !== "idle" && fetcher.formData?.get("intent") === "generate";
  const data = fetcher.data;

  function toggleEntry(id: string) {
    setSelectedIds((current) =>
      current.includes(id) ? current.filter((value) => value !== id) : [...current, id],
    );
  }

  return (
    <section className={`${panelClass} p-4`}>
      <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">生成总结</h2>
      <p className={helperTextClass}>挑一个时间范围或一条主题线索，让 AI 把记录织成一篇回顾。</p>

      <fetcher.Form method="post" className="mt-4 space-y-4">
        <input type="hidden" name="intent" value="generate" />
        <input type="hidden" name="scope" value={scope} />
        <input type="hidden" name="style" value={style} />

        <ChipGroup
          options={[
            { value: "period", label: "时间范围" },
            { value: "topic", label: "主题线索" },
          ]}
          value={scope}
          onChange={(value) => setScope(value as "period" | "topic")}
        />

        {scope === "period" ? (
          <div>
            <span className={labelClass}>时间范围</span>
            <input type="hidden" name="periodType" value={periodType} />
            <ChipGroup
              options={PERIOD_OPTIONS}
              value={periodType}
              onChange={(value) => setPeriodType(value as SummaryPeriodType)}
            />
            {periodType === "custom" ? (
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                <label className={labelClass}>
                  起始
                  <input type="date" name="startDate" required className={inputClass} />
                </label>
                <label className={labelClass}>
                  结束
                  <input type="date" name="endDate" required className={inputClass} />
                </label>
              </div>
            ) : null}
          </div>
        ) : (
          <div className="space-y-3">
            <div>
              <span className={labelClass}>标签 / 人物 / 关系</span>
              <div className="mt-1 grid gap-3 sm:grid-cols-3">
                <SuggestedInput
                  id="summary-tags"
                  name="tags"
                  optionLabel="添加已有标签"
                  options={suggestions.tags}
                  placeholder="标签"
                  selectionMode="append"
                />
                <SuggestedInput
                  id="summary-people"
                  name="people"
                  optionLabel="添加已有人物"
                  options={suggestions.people}
                  placeholder="人物"
                  selectionMode="append"
                />
                <SuggestedInput
                  id="summary-relationships"
                  name="relationships"
                  optionLabel="添加已有关系"
                  options={suggestions.relationships}
                  placeholder="关系"
                  selectionMode="append"
                />
              </div>
            </div>
            <label className={labelClass}>
              关键词
              <input
                type="text"
                name="keyword"
                placeholder="在正文 / 心情 / 地点中搜索"
                className={inputClass}
              />
            </label>
            <input type="hidden" name="entryIds" value={selectedIds.join(",")} />
            {pickerEntries.length > 0 ? (
              <details className="rounded-lg border border-gray-200 dark:border-gray-800">
                <summary className="cursor-pointer px-3 py-2 text-gray-700 text-sm dark:text-gray-300">
                  从最近记录手动勾选{selectedIds.length > 0 ? `（已选 ${selectedIds.length}）` : ""}
                </summary>
                <ul className="max-h-56 overflow-auto border-gray-100 border-t dark:border-gray-800">
                  {pickerEntries.map((entry) => (
                    <li key={entry.id}>
                      <label className="flex cursor-pointer items-center gap-2 px-3 py-1.5 text-sm hover:bg-gray-50 dark:hover:bg-gray-900">
                        <input
                          type="checkbox"
                          checked={selectedIds.includes(entry.id)}
                          onChange={() => toggleEntry(entry.id)}
                          className="h-4 w-4 rounded border-gray-300 dark:border-gray-700"
                        />
                        <span className="text-gray-400 text-xs dark:text-gray-500">
                          {entry.entryDate}
                        </span>
                        <span className="truncate text-gray-700 dark:text-gray-300">
                          {entry.title || "(无标题)"}
                        </span>
                      </label>
                    </li>
                  ))}
                </ul>
              </details>
            ) : null}
          </div>
        )}

        <div>
          <span className={labelClass}>风格</span>
          <ChipGroup
            options={STYLE_OPTIONS}
            value={style}
            onChange={(value) => setStyle(value as SummaryStyle)}
          />
        </div>

        <div className="flex items-center gap-3">
          <button type="submit" disabled={generating} className={primaryButtonClass}>
            {generating ? "生成中…" : "生成"}
          </button>
        </div>

        {data && data.intent === "generate" ? (
          <p className={statusClass(data.ok)}>{data.message}</p>
        ) : null}
      </fetcher.Form>
    </section>
  );
}

function SummaryCard({ summary }: { summary: LoadedSummary }) {
  const fetcher = useFetcher<SummaryActionData>();
  const busy = fetcher.state !== "idle";
  const scopeLabel =
    summary.scope === "topic"
      ? "主题"
      : summary.periodType && isSummaryPeriodType(summary.periodType)
        ? PERIOD_TYPE_LABELS[summary.periodType]
        : "时间范围";
  const styleLabel = isSummaryStyle(summary.style) ? STYLE_LABELS[summary.style] : summary.style;
  const range =
    summary.startDate === summary.endDate
      ? summary.startDate
      : `${summary.startDate} – ${summary.endDate}`;

  return (
    <details
      id={`summary-${summary.id}`}
      className="rounded-lg border border-gray-200 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-950"
    >
      <summary className="cursor-pointer list-none">
        <span className="font-medium text-gray-950 text-sm dark:text-gray-50">
          {summary.title || "未命名总结"}
        </span>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <span className={badgeClass()}>{scopeLabel}</span>
          <span className={badgeClass()}>{styleLabel}</span>
          <span className="text-gray-400 text-xs dark:text-gray-500">{range}</span>
          <span className="text-gray-400 text-xs dark:text-gray-500">
            · 基于 {summary.sourceEntryIds.length} 条 · 生成于 {summary.generatedAt}
          </span>
        </div>
      </summary>

      <div className="mt-3 border-gray-100 border-t pt-3 dark:border-gray-800">
        <Markdown content={summary.content} />

        {summary.sourceEntryIds.length > 0 ? (
          <div className="mt-3 flex flex-wrap gap-2">
            {summary.sourceEntryIds.slice(0, 12).map((id, index) => (
              <Link
                key={id}
                to={`/entries/${id}`}
                className="text-gray-500 text-xs hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
              >
                来源 {index + 1}
              </Link>
            ))}
          </div>
        ) : null}

        <div className="mt-3 flex items-center gap-2">
          <fetcher.Form method="post">
            <input type="hidden" name="intent" value="regenerate-summary" />
            <input type="hidden" name="id" value={summary.id} />
            <button type="submit" disabled={busy} className={subtleButtonClass}>
              {busy ? "处理中…" : "重新生成"}
            </button>
          </fetcher.Form>
          <fetcher.Form
            method="post"
            onSubmit={(event) => {
              if (!confirm("确定删除这篇总结吗？")) {
                event.preventDefault();
              }
            }}
          >
            <input type="hidden" name="intent" value="delete" />
            <input type="hidden" name="id" value={summary.id} />
            <button type="submit" disabled={busy} className={subtleButtonClass}>
              删除
            </button>
          </fetcher.Form>
        </div>

        {fetcher.data && !fetcher.data.ok ? (
          <p className={`mt-3 ${statusClass(false)}`}>{fetcher.data.message}</p>
        ) : null}
      </div>
    </details>
  );
}

function RecentInsightItem({
  entry,
}: {
  entry: Route.ComponentProps["loaderData"]["recentInsights"][number];
}) {
  const fetcher = useFetcher<SummaryActionData>();
  const busy = fetcher.state !== "idle";
  return (
    <li className="rounded-lg border border-gray-200 bg-white px-3 py-2 dark:border-gray-800 dark:bg-gray-950">
      <p className="text-gray-700 text-sm dark:text-gray-300">{entry.summary}</p>
      <div className="mt-2 flex items-center justify-between text-xs">
        <fetcher.Form method="post">
          <input type="hidden" name="intent" value="regenerate-entry" />
          <input type="hidden" name="entryId" value={entry.id} />
          <button
            type="submit"
            disabled={busy}
            className="text-gray-400 hover:text-gray-900 disabled:opacity-60 dark:hover:text-gray-100"
          >
            {busy ? "处理中…" : "重新生成"}
          </button>
        </fetcher.Form>
        <Link
          to={`/entries/${entry.id}`}
          className="text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
        >
          查看来源
        </Link>
      </div>
      {fetcher.data && !fetcher.data.ok ? (
        <p className="mt-2 text-red-600 text-xs dark:text-red-400">{fetcher.data.message}</p>
      ) : null}
    </li>
  );
}

export default function Insights({ loaderData }: Route.ComponentProps) {
  const {
    todayInsights,
    recentInsights,
    themes,
    noteCount,
    summaries,
    suggestions,
    pickerEntries,
  } = loaderData;
  const topThemes = Object.entries(themes)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8);

  return (
    <main className={pageShellClass}>
      <section className={pageSectionClass}>
        <header>
          <h1 className={pageTitleClass}>洞察</h1>
          <p className={pageLeadClass}>主动生成回顾，短摘要与线索随后展开。</p>
        </header>

        <GeneratePanel suggestions={suggestions} pickerEntries={pickerEntries} />

        <section className={`${panelClass} p-4`}>
          <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">总结回顾</h2>
          {summaries.length === 0 ? (
            <p className="mt-3 text-gray-400 text-sm dark:text-gray-500">
              还没有总结。在上面挑一个范围或主题，点「生成」试试。
            </p>
          ) : (
            <div className="mt-3 space-y-2">
              {summaries.map((summary) => (
                <SummaryCard key={summary.id} summary={summary} />
              ))}
            </div>
          )}
        </section>

        <section className={`${panelClass} p-4`}>
          <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">今日余韵</h2>
          {todayInsights.length === 0 ? (
            <p className="mt-3 text-gray-400 text-sm dark:text-gray-500">
              写下一些内容后，Sillage 会帮你看见它们之间的线索。
            </p>
          ) : (
            <ul className="mt-3 space-y-3">
              {todayInsights.map((entry) => (
                <li key={entry.id} className="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-950">
                  <p className="text-gray-700 text-sm dark:text-gray-300">{entry.summary}</p>
                  <Link
                    to={`/entries/${entry.id}`}
                    className="mt-2 inline-block text-gray-400 text-xs hover:text-gray-900 dark:hover:text-gray-100"
                  >
                    查看来源
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className={`${panelClass} p-4`}>
          <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">最近洞察</h2>
          {recentInsights.length === 0 ? (
            <p className="mt-3 text-gray-400 text-sm dark:text-gray-500">还没有可展示的洞察。</p>
          ) : (
            <ul className="mt-3 space-y-3">
              {recentInsights.map((entry) => (
                <RecentInsightItem key={entry.id} entry={entry} />
              ))}
            </ul>
          )}
        </section>

        <section className={`${panelClass} p-4`}>
          <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">萦绕主题</h2>
          {topThemes.length === 0 ? (
            <p className="mt-3 text-gray-400 text-sm dark:text-gray-500">
              更多记录之后，这里会浮现反复出现的主题。
            </p>
          ) : (
            <div className="mt-3 flex flex-wrap gap-2">
              {topThemes.map(([tag, count]) => (
                <span
                  key={tag}
                  className="rounded-full bg-gray-100 px-3 py-1 text-gray-600 text-sm dark:bg-gray-800 dark:text-gray-300"
                >
                  #{tag} · {count}
                </span>
              ))}
            </div>
          )}
          <p className="mt-3 text-gray-400 text-xs dark:text-gray-500">
            已整理 {noteCount} 篇笔记。
          </p>
        </section>

        <div className={`${subtlePanelClass} px-4 py-3 text-gray-500 text-sm dark:text-gray-400`}>
          记忆问答已独立放在“记忆”入口，洞察页聚焦主动生成的回顾与浮现的主题。
        </div>
      </section>
    </main>
  );
}
