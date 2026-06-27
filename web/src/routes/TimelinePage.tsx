import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { CalendarView } from "../components/CalendarView";
import { EntryCard } from "../components/EntryCard";
import {
  inputClass,
  pageLeadClass,
  pageSectionClass,
  pageTitleClass,
  subtlePanelClass,
  wideShellClass,
} from "../components/ui";
import type { Memo } from "../lib/api";
import { monthGrid, todayISO, yearsBetween } from "../lib/date";
import {
  entriesByDate,
  entryDateCounts,
  excerpt,
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

function OnThisDay({ entries, today }: { entries: Memo[]; today: string }) {
  return (
    <section className="rounded-xl border border-gray-200 bg-gray-50 p-4 dark:border-gray-700/70 dark:bg-gray-800/60">
      <h2 className="font-medium text-gray-500 text-xs dark:text-gray-400">
        那年今日
      </h2>
      <ul className="mt-2 space-y-1">
        {entries.map((memo) => (
          <li key={memo.id}>
            <Link
              to={`/entries/${memo.id}`}
              className="block rounded-lg px-2 py-1.5 text-gray-600 text-sm transition hover:bg-gray-100 hover:text-gray-900 dark:text-gray-300 dark:hover:bg-gray-800"
            >
              <span className="text-gray-400">
                {yearsBetween(memo.entryDate, today)}年前
              </span>
              <span className="mx-1.5 text-gray-300 dark:text-gray-600">·</span>
              {excerpt(memo.content, 48) || "空白记录"}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

export function TimelinePage() {
  const [searchParams] = useSearchParams();
  const { memos } = useMemos();
  const [query, setQuery] = useState("");
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
          <ListView
            memos={memos}
            today={today}
            query={query}
            onQuery={setQuery}
          />
        )}
      </section>
    </main>
  );
}

function ListView({
  memos,
  today,
  query,
  onQuery,
}: {
  memos: Memo[];
  today: string;
  query: string;
  onQuery: (value: string) => void;
}) {
  const trimmed = query.trim().toLowerCase();
  const active = memos.filter(isActive);
  const filtered = trimmed
    ? active.filter((memo) => memo.content.toLowerCase().includes(trimmed))
    : active.slice(0, 120);
  const memories = onThisDay(memos, today);

  return (
    <div className="space-y-6">
      <input
        type="search"
        value={query}
        onChange={(event) => onQuery(event.target.value)}
        placeholder="搜索记录…"
        className={`${inputClass} mt-0`}
      />
      {!trimmed && memories.length > 0 ? (
        <OnThisDay entries={memories} today={today} />
      ) : null}
      <section className="min-w-0">
        {filtered.length === 0 ? (
          <div
            className={`${subtlePanelClass} px-4 py-10 text-center text-gray-500 text-sm dark:text-gray-400`}
          >
            {trimmed ? "没有匹配的记录。" : "还没有记录。可以先写一条记录。"}
          </div>
        ) : (
          <ul className="divide-y divide-gray-200 dark:divide-gray-800">
            {filtered.map((memo) => (
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
  const year = Number(searchParams.get("y")) || Number(today.slice(0, 4));
  const month = Number(searchParams.get("m")) || Number(today.slice(5, 7));
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
