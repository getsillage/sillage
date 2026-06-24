import { Link } from "react-router";
import { panelClass, subtlePanelClass } from "~/components/ui";
import type { EntryFormSuggestions } from "~/lib/product/entry-suggestions";
import { type InsightEntry, RecentInsightItem } from "./RecentInsightItem";
import { type LoadedSummary, SummaryCard } from "./SummaryCard";
import { type PickerEntry, SummaryGenerator } from "./SummaryGenerator";

interface ReviewTabProps {
  todayInsights: InsightEntry[];
  recentInsights: InsightEntry[];
  themes: Record<string, number>;
  noteCount: number;
  suggestions: EntryFormSuggestions;
  pickerEntries: PickerEntry[];
  summaries: LoadedSummary[];
}

/** The 照见 tab: AI's proactive output — generated reviews, today's lingering, themes. */
export function ReviewTab({
  todayInsights,
  recentInsights,
  themes,
  noteCount,
  suggestions,
  pickerEntries,
  summaries,
}: ReviewTabProps) {
  const topThemes = Object.entries(themes)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8);

  return (
    <>
      <SummaryGenerator suggestions={suggestions} pickerEntries={pickerEntries} />

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
              <Link
                key={tag}
                to={`/timeline?tag=${encodeURIComponent(tag)}`}
                className="rounded-full bg-gray-100 px-3 py-1 text-gray-600 text-sm transition hover:text-gray-950 dark:bg-gray-800 dark:text-gray-300 dark:hover:text-gray-50"
              >
                #{tag} · {count}
              </Link>
            ))}
          </div>
        )}
        <p className="mt-3 text-gray-400 text-xs dark:text-gray-500">已整理 {noteCount} 篇笔记。</p>
      </section>

      <div className={`${subtlePanelClass} px-4 py-3 text-gray-500 text-sm dark:text-gray-400`}>
        想主动搜索或提问，去上方的「探寻」。这里聚焦 AI 主动照见的线索与浮现的主题。
      </div>
    </>
  );
}
