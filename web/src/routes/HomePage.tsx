import { Link } from "react-router-dom";
import { EntryCard } from "../components/EntryCard";
import { EntryComposer } from "../components/EntryComposer";
import type { Memo } from "../lib/api";
import { todayISO, yearsBetween } from "../lib/date";
import { excerpt, isActive, onThisDay } from "../lib/memos";
import { useMemos } from "../state/MemosContext";

function MemorySection({ entries, today }: { entries: Memo[]; today: string }) {
  if (entries.length === 0) {
    return null;
  }

  return (
    <section className="rounded-lg bg-clay-50 px-4 py-4 dark:bg-clay-900">
      <h2 className="font-serif text-clay-600 text-sm dark:text-clay-300">
        那年今日
      </h2>
      <ul className="mt-2 space-y-1">
        {entries.map((memo) => (
          <li key={memo.id}>
            <Link
              to={`/entries/${memo.id}`}
              className="block rounded-md px-2 py-1.5 text-gray-700 text-sm transition hover:bg-clay-100 dark:text-gray-200 dark:hover:bg-gray-800"
            >
              <span className="text-clay-600 dark:text-clay-300">
                {yearsBetween(memo.entryDate, today)}年前
              </span>
              <span className="mx-1.5 text-gray-400">·</span>
              {excerpt(memo.content, 56) || "空白记录"}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

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
        <h2 className="font-medium text-gray-600 text-sm dark:text-gray-300">
          {title}
        </h2>
        {showAllLink ? (
          <Link
            to="/timeline"
            className="text-gray-500 text-xs hover:text-celadon-700 dark:text-gray-400 dark:hover:text-celadon-200"
          >
            查看全部
          </Link>
        ) : null}
      </div>
      {entries.length === 0 ? (
        <div className="rounded-lg bg-gray-100/60 px-4 py-8 text-center text-gray-500 text-sm dark:bg-gray-900/50 dark:text-gray-400">
          {empty}
        </div>
      ) : (
        <ul className="divide-y divide-gray-200 dark:divide-gray-800">
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
  const active = memos.filter(isActive);
  const chronological = [...active].sort((a, b) => {
    if (a.entryDate !== b.entryDate) {
      return b.entryDate.localeCompare(a.entryDate);
    }
    return b.createdAt.localeCompare(a.createdAt);
  });
  const todayEntries = chronological.filter((memo) => memo.entryDate === today);
  const recentEntries = chronological
    .filter((memo) => memo.entryDate !== today)
    .slice(0, 12);
  const memories = onThisDay(memos, today);

  return (
    <main className="mx-auto w-full max-w-3xl px-4 pt-8 pb-32 sm:px-6 sm:py-10">
      <div className="space-y-8">
        <header>
          <p className="text-gray-500 text-xs dark:text-gray-400">{today}</p>
          <h1 className="mt-1 font-serif text-2xl text-gray-900 sm:text-3xl dark:text-gray-50">
            今天想记录什么？
          </h1>
        </header>

        <section className="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900 sm:p-5">
          <EntryComposer
            submitLabel="保存"
            onSubmit={async (input) => {
              await create(input);
            }}
            onUpload={upload}
          />
        </section>

        <MemorySection entries={memories} today={today} />

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
