import { useMemo } from "react";
import { Link } from "react-router-dom";
import { EntryCard } from "../components/EntryCard";
import { EntryComposer } from "../components/EntryComposer";
import { OnThisDay } from "../components/OnThisDay";
import {
  emptyStateClass,
  ghostLinkClass,
  panelClass,
  readingShellClass,
} from "../components/ui";
import type { Memo } from "../lib/api";
import { todayISO } from "../lib/date";
import { isActive, onThisDay } from "../lib/memos";
import { useMemos } from "../state/MemosContext";

function EntrySection({
  title,
  entries,
  empty,
  showAllLink = false,
}: {
  title: string;
  entries: Memo[];
  empty: string;
  showAllLink?: boolean;
}) {
  return (
    <section className="min-w-0 pr-16 sm:pr-0">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 className="font-medium text-gray-700 text-sm dark:text-gray-300">
          {title}
        </h2>
        {showAllLink ? (
          <Link to="/timeline" className={`${ghostLinkClass} text-xs`}>
            查看全部
          </Link>
        ) : null}
      </div>
      {entries.length === 0 ? (
        <div className={emptyStateClass}>{empty}</div>
      ) : (
        <ul className="rounded-lg border border-gray-200/80 bg-white/70 p-1 shadow-sm shadow-gray-900/[0.02] dark:border-gray-800 dark:bg-gray-900/45">
          {entries.map((memo) => (
            <li key={memo.id}>
              <EntryCard memo={memo} openOnCardClick />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export function HomePage() {
  const { memos, loading, error, create, upload } = useMemos();
  const today = todayISO();
  const { todayEntries, recentEntries, memories } = useMemo(() => {
    const chronological = memos.filter(isActive).sort((a, b) => {
      if (a.entryDate !== b.entryDate) {
        return b.entryDate.localeCompare(a.entryDate);
      }
      return b.createdAt.localeCompare(a.createdAt);
    });
    return {
      todayEntries: chronological.filter((memo) => memo.entryDate === today),
      recentEntries: chronological
        .filter((memo) => memo.entryDate !== today)
        .slice(0, 12),
      memories: onThisDay(memos, today),
    };
  }, [memos, today]);

  return (
    <main className={`${readingShellClass} pb-32`}>
      <div className="space-y-7">
        <header>
          <p className="text-gray-500 text-xs dark:text-gray-400">{today}</p>
          <h1 className="mt-1 font-semibold text-2xl text-gray-900 tracking-tight sm:text-[1.75rem] dark:text-gray-50">
            今天想记录什么？
          </h1>
        </header>

        <section className={`${panelClass} p-3 sm:p-4`}>
          <EntryComposer
            submitLabel="保存"
            onSubmit={async (input) => {
              await create(input);
            }}
            onUpload={upload}
          />
        </section>

        <OnThisDay entries={memories} today={today} />

        {loading ? (
          <p className="text-gray-400 text-sm dark:text-gray-500">
            正在读取记录…
          </p>
        ) : null}
        {error ? (
          <p className="text-red-600 text-sm dark:text-red-400">{error}</p>
        ) : null}

        <EntrySection
          title="今天的记录"
          entries={todayEntries}
          empty="今天还没有记录。可以先写下第一条。"
        />
        <EntrySection
          title="最近历史"
          entries={recentEntries}
          empty="还没有更早的记录。"
          showAllLink
        />
      </div>
    </main>
  );
}
