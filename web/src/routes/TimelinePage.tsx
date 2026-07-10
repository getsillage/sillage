import { Archive, CalendarDays, List, Search, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { CalendarView } from "../components/CalendarView";
import { EntryCard } from "../components/EntryCard";
import { OnThisDay } from "../components/OnThisDay";
import {
  emptyStateClass,
  ghostLinkClass,
  iconButtonClass,
  pageLeadClass,
  pageSectionClass,
  pageTitleClass,
  secondaryButtonClass,
  segmentedControlClass,
  segmentedItemClass,
  skeletonClass,
  wideShellClass,
} from "../components/ui";
import type { Memo } from "../lib/api";
import {
  formatEntryDate,
  monthGrid,
  normalizeYearMonth,
  todayISO,
} from "../lib/date";
import {
  entriesByDate,
  entryDateCounts,
  isActive,
  onThisDay,
} from "../lib/memos";
import { useMemos } from "../state/MemosContext";

function ViewToggle({ calendar }: { calendar: boolean }) {
  return (
    <fieldset className={segmentedControlClass}>
      <legend className="sr-only">历史视图</legend>
      <Link
        to="/timeline"
        className={segmentedItemClass(!calendar)}
        aria-current={!calendar ? "page" : undefined}
      >
        <List className="h-4 w-4" aria-hidden="true" />
        列表
      </Link>
      <Link
        to="/timeline?view=calendar"
        className={segmentedItemClass(calendar)}
        aria-current={calendar ? "page" : undefined}
      >
        <CalendarDays className="h-4 w-4" aria-hidden="true" />
        日历
      </Link>
    </fieldset>
  );
}

export function TimelinePage() {
  const [searchParams] = useSearchParams();
  const { memos, loading, error, refresh, loadAll } = useMemos();
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
            loading={loading}
            error={error}
            refresh={refresh}
            loadAll={loadAll}
          />
        ) : (
          <ListView
            memos={memos}
            today={today}
            loading={loading}
            archived={searchParams.get("filter") === "archived"}
          />
        )}
      </section>
    </main>
  );
}

function ListView({
  memos,
  today,
  loading,
  archived,
}: {
  memos: Memo[];
  today: string;
  loading: boolean;
  archived: boolean;
}) {
  const { error, refresh, search, loadMore, hasMore, loadingMore } = useMemos();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Memo[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState("");
  const trimmed = query.trim();
  const visibleMemos = memos.filter((memo) =>
    archived ? Boolean(memo.archivedAt && !memo.deletedAt) : isActive(memo),
  );
  const memories = onThisDay(memos, today);
  const archivedCount = memos.filter(
    (memo) => memo.archivedAt && !memo.deletedAt,
  ).length;

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
    setResults(null);
    const handle = setTimeout(() => {
      search(trimmed, archived)
        .then((found) => {
          if (!cancelled) {
            setResults(
              found.filter((memo) =>
                archived
                  ? Boolean(memo.archivedAt && !memo.deletedAt)
                  : isActive(memo),
              ),
            );
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
  }, [trimmed, search, archived]);

  // The full loaded set in reverse-chronological order; older pages stream in
  // via the infinite-scroll sentinel below. Search results bypass pagination.
  const list = trimmed ? (results ?? []) : visibleMemos;
  const groups = useMemo(() => groupEntries(list), [list]);
  const showLoadMore = !trimmed && hasMore;
  const listError = !trimmed ? error : "";
  const retryingList = hasMore ? loadingMore : loading;
  const sentinelRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (
      !showLoadMore ||
      listError ||
      typeof IntersectionObserver === "undefined"
    ) {
      return;
    }
    const el = sentinelRef.current;
    if (!el) {
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          void loadMore().catch(() => undefined);
        }
      },
      { rootMargin: "400px" },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [showLoadMore, listError, loadMore]);

  return (
    <div className="space-y-5">
      <div className="relative">
        <label htmlFor="timeline-search" className="sr-only">
          搜索记录
        </label>
        <Search
          className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-gray-400"
          aria-hidden="true"
        />
        <input
          id="timeline-search"
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="搜索记录…"
          className="block h-11 w-full rounded-lg border border-gray-200 bg-white/80 pr-11 pl-10 text-sm text-gray-900 transition-colors placeholder:text-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-300/55 dark:border-gray-700 dark:bg-gray-900/80 dark:text-gray-50 dark:placeholder:text-gray-500 dark:focus:border-gray-500 dark:focus:ring-gray-600/50"
        />
        {query ? (
          <button
            type="button"
            onClick={() => setQuery("")}
            className={`${iconButtonClass} absolute top-1/2 right-0.5 -translate-y-1/2`}
            aria-label="清除搜索"
            title="清除搜索"
          >
            <X className="h-4 w-4" />
          </button>
        ) : null}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <fieldset className={segmentedControlClass}>
          <legend className="sr-only">记录状态</legend>
          <Link
            to="/timeline"
            className={segmentedItemClass(!archived)}
            aria-current={!archived ? "page" : undefined}
          >
            当前记录
          </Link>
          <Link
            to="/timeline?filter=archived"
            className={segmentedItemClass(archived)}
            aria-current={archived ? "page" : undefined}
          >
            <Archive className="h-4 w-4" aria-hidden="true" />
            已归档{archivedCount > 0 ? ` ${archivedCount}` : ""}
          </Link>
        </fieldset>
        <p
          className="text-gray-500 text-xs dark:text-gray-400"
          aria-live="polite"
        >
          {searching
            ? "正在搜索"
            : trimmed
              ? `找到 ${list.length} 条记录`
              : listError && list.length === 0
                ? "记录读取失败"
                : `${list.length} 条${archived ? "已归档" : "当前"}记录`}
        </p>
      </div>

      {!archived && !trimmed && memories.length > 0 ? (
        <OnThisDay entries={memories} today={today} />
      ) : null}
      {searchError ? (
        <p role="alert" className="text-red-600 text-sm dark:text-red-400">
          {searchError}
        </p>
      ) : null}
      <section className="min-w-0 pr-14 sm:pr-0">
        {(loading && !trimmed) || (searching && results === null) ? (
          <TimelineSkeleton />
        ) : groups.length === 0 && !listError ? (
          <div className={emptyStateClass}>
            {trimmed
              ? "没有匹配的记录。换一个词试试。"
              : archived
                ? "还没有归档记录。"
                : "还没有记录。可以先写一条记录。"}
          </div>
        ) : (
          <div className="space-y-7">
            {groups.map((group) => (
              <section
                key={group.key}
                aria-labelledby={`memo-group-${group.key}`}
              >
                <div className="mb-2 flex items-baseline justify-between gap-3 px-1">
                  <h2
                    id={`memo-group-${group.key}`}
                    className="font-medium text-gray-800 text-sm dark:text-gray-200"
                  >
                    {group.date ? formatEntryDate(group.date, today) : "置顶"}
                  </h2>
                  <span className="text-gray-400 text-xs dark:text-gray-500">
                    {group.entries.length} 条
                  </span>
                </div>
                <ul className="divide-y divide-gray-200/70 rounded-lg border border-gray-200/80 bg-white/70 p-1 shadow-sm shadow-gray-900/[0.02] dark:divide-gray-800 dark:border-gray-800 dark:bg-gray-900/45">
                  {group.entries.map((memo) => (
                    <li key={memo.id}>
                      <EntryCard
                        memo={memo}
                        openOnCardClick
                        grouped={Boolean(group.date)}
                      />
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        )}
        {listError ? (
          <div className="mt-5 flex flex-wrap items-center gap-3 rounded-lg border border-red-200 bg-red-50/70 p-3 text-red-700 text-sm dark:border-red-900/60 dark:bg-red-950/25 dark:text-red-300">
            <p role="alert" className="min-w-0 flex-1">
              {listError}
            </p>
            <button
              type="button"
              className={secondaryButtonClass}
              disabled={retryingList}
              onClick={() => {
                if (hasMore) {
                  void loadMore().catch(() => undefined);
                  return;
                }
                void refresh();
              }}
            >
              {retryingList
                ? "正在重试…"
                : hasMore
                  ? "重试加载更多"
                  : "重新加载记录"}
            </button>
          </div>
        ) : showLoadMore ? (
          <div
            ref={sentinelRef}
            className="flex justify-center py-5 text-gray-500 text-sm dark:text-gray-400"
            aria-live="polite"
          >
            {loadingMore ? (
              "正在加载更多…"
            ) : (
              <button
                type="button"
                onClick={() => void loadMore().catch(() => undefined)}
                className={ghostLinkClass}
              >
                加载更多
              </button>
            )}
          </div>
        ) : null}
      </section>
    </div>
  );
}

interface MemoGroup {
  key: string;
  date: string | null;
  entries: Memo[];
}

function chronologicalMemos(memos: Memo[]): Memo[] {
  return [...memos].sort((a, b) => {
    if (a.entryDate !== b.entryDate) {
      return b.entryDate.localeCompare(a.entryDate);
    }
    return b.createdAt.localeCompare(a.createdAt);
  });
}

function groupEntries(memos: Memo[]): MemoGroup[] {
  const pinned = chronologicalMemos(
    memos.filter((memo) => Boolean(memo.pinnedAt)),
  );
  const chronological = chronologicalMemos(
    memos.filter((memo) => !memo.pinnedAt),
  );
  const groups = new Map<string, Memo[]>();
  for (const memo of chronological) {
    const entries = groups.get(memo.entryDate) ?? [];
    entries.push(memo);
    groups.set(memo.entryDate, entries);
  }
  const dateGroups: MemoGroup[] = [...groups].map(([date, entries]) => ({
    key: date,
    date,
    entries,
  }));
  return pinned.length > 0
    ? [{ key: "pinned", date: null, entries: pinned }, ...dateGroups]
    : dateGroups;
}

function TimelineSkeleton() {
  return (
    <div className="space-y-7" role="status">
      <span className="sr-only">正在读取记录</span>
      {[0, 1, 2].map((group) => (
        <div key={group} className="space-y-2">
          <div className={`${skeletonClass} h-4 w-32`} />
          <div className="space-y-4 rounded-lg border border-gray-200/70 bg-white/50 p-4 dark:border-gray-800 dark:bg-gray-900/40">
            <div className={`${skeletonClass} h-3 w-24`} />
            <div className={`${skeletonClass} h-5 w-3/4`} />
          </div>
        </div>
      ))}
    </div>
  );
}

function CalendarMonth({
  searchParams,
  memos,
  today,
  loading,
  error,
  refresh,
  loadAll,
}: {
  searchParams: URLSearchParams;
  memos: Memo[];
  today: string;
  loading: boolean;
  error: string;
  refresh: () => Promise<void>;
  loadAll: () => Promise<void>;
}) {
  const [fullLoadState, setFullLoadState] = useState<
    "loading" | "ready" | "error"
  >("loading");
  const [fullLoadError, setFullLoadError] = useState("");
  const fullLoadRequestRef = useRef(0);

  const startFullLoad = useCallback(() => {
    const request = fullLoadRequestRef.current + 1;
    fullLoadRequestRef.current = request;
    setFullLoadState("loading");
    setFullLoadError("");
    void loadAll()
      .then(() => {
        if (fullLoadRequestRef.current === request) {
          setFullLoadState("ready");
        }
      })
      .catch((cause) => {
        if (fullLoadRequestRef.current === request) {
          setFullLoadError(
            cause instanceof Error ? cause.message : "读取完整历史失败",
          );
          setFullLoadState("error");
        }
      });
  }, [loadAll]);

  const retryFullLoad = useCallback(() => {
    fullLoadRequestRef.current += 1;
    setFullLoadState("loading");
    setFullLoadError("");
    // A successful refresh clears the context error and lets the effect below
    // continue with loadAll. Keep rejection contained for future refresh APIs.
    void refresh().catch(() => undefined);
  }, [refresh]);

  useEffect(() => {
    if (loading) {
      fullLoadRequestRef.current += 1;
      setFullLoadState("loading");
      return;
    }
    if (error) {
      fullLoadRequestRef.current += 1;
      setFullLoadError(error);
      setFullLoadState("error");
      return;
    }
    startFullLoad();
    return () => {
      fullLoadRequestRef.current += 1;
    };
  }, [error, loading, startFullLoad]);

  if (fullLoadState === "loading") {
    return (
      <div
        className="py-12 text-center text-gray-500 text-sm dark:text-gray-400"
        role="status"
      >
        {memos.length > 0
          ? `已读取 ${memos.length} 条，正在继续…`
          : "正在读取完整历史…"}
      </div>
    );
  }

  if (fullLoadState === "error") {
    return (
      <div className={`${emptyStateClass} space-y-3`}>
        <p role="alert">{fullLoadError}</p>
        <button
          type="button"
          className={secondaryButtonClass}
          onClick={retryFullLoad}
        >
          重新加载完整历史
        </button>
      </div>
    );
  }

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
