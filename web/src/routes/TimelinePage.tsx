import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { CalendarView } from "../components/CalendarView";
import { EntryCard } from "../components/EntryCard";
import { OnThisDay } from "../components/OnThisDay";
import {
  emptyStateClass,
  inputClass,
  pageLeadClass,
  pageSectionClass,
  pageTitleClass,
  wideShellClass,
} from "../components/ui";
import type { Memo } from "../lib/api";
import { monthGrid, normalizeYearMonth, todayISO } from "../lib/date";
import {
  entriesByDate,
  entryDateCounts,
  isActive,
  onThisDay,
} from "../lib/memos";
import { useMemos } from "../state/MemosContext";

function viewToggleClass(active: boolean): string {
  return active
    ? "rounded-md bg-gray-200 px-3 py-1 text-sm text-gray-900 dark:bg-gray-700 dark:text-gray-50"
    : "rounded-md px-3 py-1 text-gray-500 text-sm hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100";
}

function ViewToggle({ calendar }: { calendar: boolean }) {
  return (
    <div className="flex gap-0.5 rounded-lg border border-gray-200 bg-white p-0.5 dark:border-gray-700 dark:bg-gray-800">
      <Link to="/timeline" className={viewToggleClass(!calendar)}>
        列表
      </Link>
      <Link to="/timeline?view=calendar" className={viewToggleClass(calendar)}>
        日历
      </Link>
    </div>
  );
}

export function TimelinePage() {
  const [searchParams] = useSearchParams();
  const { memos } = useMemos();
  const today = todayISO();
  const calendar = searchParams.get("view") === "calendar";

  return (
    <main className={wideShellClass}>
      <section className={pageSectionClass}>
        <header className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className={pageTitleClass}>历史</h1>
            <p className={pageLeadClass}>按时间查看所有记录。</p>
          </div>
          <ViewToggle calendar={calendar} />
        </header>

        {calendar ? (
          <CalendarMonth
            searchParams={searchParams}
            memos={memos}
            today={today}
          />
        ) : (
          <ListView memos={memos} today={today} />
        )}
      </section>
    </main>
  );
}

function ListView({ memos, today }: { memos: Memo[]; today: string }) {
  const { search } = useMemos();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Memo[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState("");
  const trimmed = query.trim();
  const active = memos.filter(isActive);
  const memories = onThisDay(memos, today);

  // Debounced server-side FTS search; empty query falls back to the recent list.
  useEffect(() => {
    if (!trimmed) {
      setResults(null);
      setSearchError("");
      setSearching(false);
      return;
    }
    let cancelled = false;
    setSearching(true);
    const handle = setTimeout(() => {
      search(trimmed)
        .then((found) => {
          if (!cancelled) {
            setResults(found.filter(isActive));
            setSearchError("");
          }
        })
        .catch((cause) => {
          if (!cancelled) {
            setResults([]);
            setSearchError(cause instanceof Error ? cause.message : "搜索失败");
          }
        })
        .finally(() => {
          if (!cancelled) {
            setSearching(false);
          }
        });
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(handle);
    };
  }, [trimmed, search]);

  const list = trimmed ? (results ?? []) : active.slice(0, 120);

  return (
    <div className="space-y-6">
      <input
        type="search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="搜索记录…"
        className={`${inputClass} mt-0`}
      />
      {!trimmed && memories.length > 0 ? (
        <OnThisDay entries={memories} today={today} />
      ) : null}
      {searchError ? (
        <p className="text-red-600 text-sm dark:text-red-400">{searchError}</p>
      ) : null}
      <section className="min-w-0">
        {searching && list.length === 0 ? (
          <div className={emptyStateClass}>正在搜索…</div>
        ) : list.length === 0 ? (
          <div className={emptyStateClass}>
            {trimmed ? "没有匹配的记录。" : "还没有记录。可以先写一条记录。"}
          </div>
        ) : (
          <ul className="divide-y divide-gray-200 dark:divide-gray-800">
            {list.map((memo) => (
              <li key={memo.id}>
                <EntryCard memo={memo} openOnCardClick />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function CalendarMonth({
  searchParams,
  memos,
  today,
}: {
  searchParams: URLSearchParams;
  memos: Memo[];
  today: string;
}) {
  const rawYear = queryNumber(searchParams, "y", Number(today.slice(0, 4)));
  const rawMonth = queryNumber(searchParams, "m", Number(today.slice(5, 7)));
  const { year, month } = normalizeYearMonth(rawYear, rawMonth);
  const selectedDate = searchParams.get("date");
  return (
    <CalendarView
      year={year}
      month={month}
      today={today}
      selectedDate={selectedDate}
      weeks={monthGrid(year, month)}
      counts={entryDateCounts(memos)}
      dayEntries={selectedDate ? entriesByDate(memos, selectedDate) : []}
    />
  );
}

function queryNumber(
  searchParams: URLSearchParams,
  key: string,
  fallback: number,
): number {
  const raw = searchParams.get(key);
  if (raw === null || raw.trim() === "") {
    return fallback;
  }
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : fallback;
}
