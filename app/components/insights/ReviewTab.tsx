import { Link } from "react-router";
import { subtlePanelClass } from "~/components/ui";
import type { EntryFormSuggestions } from "~/lib/product/entry-suggestions";
import { type LoadedSummary, SummaryCard } from "./SummaryCard";
import { type PickerEntry, SummaryGenerator } from "./SummaryGenerator";

interface ReviewTabProps {
  themes: Record<string, number>;
  noteCount: number;
  suggestions: EntryFormSuggestions;
  pickerEntries: PickerEntry[];
  summaries: LoadedSummary[];
}

/** The 照见 page: AI's proactive output — generated reviews and recurring themes. */
export function ReviewTab({
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

      <div className="space-y-6">
        <section>
          <h2 className="font-serif text-gray-900 text-lg dark:text-gray-50">总结回顾</h2>
          {summaries.length === 0 ? (
            <p className="mt-3 text-gray-400 text-sm dark:text-gray-500">
              还没有总结。在上面挑一个范围或主题，点「生成」试试。
            </p>
          ) : (
            <div className="mt-3 space-y-3">
              {summaries.map((summary) => (
                <SummaryCard key={summary.id} summary={summary} />
              ))}
            </div>
          )}
        </section>

        <aside className="space-y-4">
          <section className="border-gray-200 border-t pt-5 dark:border-gray-800">
            <h2 className="font-serif text-gray-900 text-lg dark:text-gray-50">萦绕主题</h2>
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
                    className="rounded-full bg-gray-100 px-3 py-1 text-gray-600 text-sm transition hover:bg-celadon-50 hover:text-celadon-800 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-celadon-900/40 dark:hover:text-celadon-200"
                  >
                    #{tag} · {count}
                  </Link>
                ))}
              </div>
            )}
            <p className="mt-3 text-gray-400 text-xs dark:text-gray-500">
              已整理 {noteCount} 篇笔记。
            </p>
          </section>

          <Link
            to="/ask"
            className={`${subtlePanelClass} block px-4 py-3 text-gray-500 text-sm transition hover:border-gray-300 hover:text-gray-900 dark:text-gray-400 dark:hover:border-gray-700 dark:hover:text-gray-100`}
          >
            想主动搜索或提问，去「探寻」。这里聚焦 AI 主动照见的线索与浮现的主题。
          </Link>
        </aside>
      </div>
    </>
  );
}
