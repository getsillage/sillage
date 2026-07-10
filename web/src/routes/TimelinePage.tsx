import { Archive, CalendarDays, List, Search, Star, X } from "lucide-react";
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
import type { Memo, MemoListOptions } from "../lib/api";
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
  mergeMemos,
  onThisDay,
} from "../lib/memos";
import { useMemos } from "../state/MemosContext";

type TimelineFilter = "active" | "archived" | "favorite";

function timelineFilter(searchParams: URLSearchParams): TimelineFilter {
  const filter = searchParams.get("filter");
  return filter === "archived" || filter === "favorite" ? filter : "active";
}

function listOptionsFor(filter: TimelineFilter): MemoListOptions {
  if (filter === "favorite") {
    return { favorited: true };
  }
  return {
    archived: filter === "archived",
    favorited: false,
  };
}

function ViewToggle({ calendar }: { calendar: boolean }) {
  return (
    <fieldset className={segmentedControlClass}>
      <legend className="sr-only">全部记录视图</legend>
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
  const filter = timelineFilter(searchParams);

  return (
    <main className={wideShellClass}>
      <section className={pageSectionClass}>
        <header className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className={pageTitleClass}>全部记录</h1>
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
          <ListView key={filter} today={today} filter={filter} />
        )}
      </section>
    </main>
  );
}

function useTimelineMemoList(filter: TimelineFilter) {
  const context = useMemos();
  const { listPage } = context;
  const options = useMemo(() => listOptionsFor(filter), [filter]);
  const [memos, setMemos] = useState<Memo[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState("");
  const requestSeqRef = useRef(0);
  const cursorRef = useRef("");
  const loadMoreRequestRef = useRef<Promise<boolean> | null>(null);

  const refresh = useCallback(async () => {
    const request = ++requestSeqRef.current;
    loadMoreRequestRef.current = null;
    cursorRef.current = "";
    setMemos([]);
    setLoading(true);
    setLoadingMore(false);
    setHasMore(false);
    setError("");
    try {
      const res = await listPage(options);
      if (request !== requestSeqRef.current) {
        return;
      }
      setMemos(res.memos);
      cursorRef.current = res.nextCursor ?? "";
      setHasMore(Boolean(res.nextCursor));
    } catch (cause) {
      if (request === requestSeqRef.current) {
        setError(cause instanceof Error ? cause.message : "读取记录失败");
      }
    } finally {
      if (request === requestSeqRef.current) {
        setLoading(false);
      }
    }
  }, [listPage, options]);

  useEffect(() => {
    if (filter === "active") {
      requestSeqRef.current += 1;
      loadMoreRequestRef.current = null;
      return;
    }
    void refresh();
    return () => {
      requestSeqRef.current += 1;
      loadMoreRequestRef.current = null;
    };
  }, [filter, refresh]);

  const loadMore = useCallback((): Promise<boolean> => {
    const cursor = cursorRef.current;
    if (!cursor) {
      return Promise.resolve(false);
    }
    if (loadMoreRequestRef.current) {
      return loadMoreRequestRef.current;
    }
    const request = requestSeqRef.current;
    setLoadingMore(true);
    let promise: Promise<boolean>;
    promise = listPage(options, cursor)
      .then((res) => {
        if (request !== requestSeqRef.current || cursor !== cursorRef.current) {
          return Boolean(cursorRef.current);
        }
        setMemos((current) => mergeMemos(current, res.memos));
        cursorRef.current = res.nextCursor ?? "";
        setHasMore(Boolean(res.nextCursor));
        setError("");
        return Boolean(res.nextCursor);
      })
      .catch((cause) => {
        if (request !== requestSeqRef.current) {
          return Boolean(cursorRef.current);
        }
        const nextError =
          cause instanceof Error ? cause : new Error("读取更多记录失败");
        setError(nextError.message);
        throw nextError;
      })
      .finally(() => {
        if (loadMoreRequestRef.current === promise) {
          loadMoreRequestRef.current = null;
          setLoadingMore(false);
        }
      });
    loadMoreRequestRef.current = promise;
    return promise;
  }, [listPage, options]);

  const activeMemos = useMemo(
    () => context.memos.filter(isActive),
    [context.memos],
  );

  if (filter === "active") {
    return {
      memos: activeMemos,
      loading: context.loading,
      loadingMore: context.loadingMore,
      hasMore: context.hasMore,
      error: context.error,
      refresh: context.refresh,
      loadMore: context.loadMore,
    };
  }

  return {
    memos,
    loading,
    loadingMore,
    hasMore,
    error,
    refresh,
    loadMore,
  };
}

function ListView({
  today,
  filter,
}: {
  today: string;
  filter: TimelineFilter;
}) {
  const { search } = useMemos();
  const { memos, loading, error, refresh, loadMore, hasMore, loadingMore } =
    useTimelineMemoList(filter);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Memo[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState("");
  const trimmed = query.trim();
  const memories = onThisDay(memos, today);
  const options = useMemo(() => listOptionsFor(filter), [filter]);

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
    setSearchError("");
    const handle = setTimeout(() => {
      search(trimmed, options)
        .then((found) => {
          if (!cancelled) {
            setResults(found);
            setSearchError("");
          }
        })
        .catch((cause) => {
          if (!cancelled) {
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
  }, [trimmed, search, options]);

  // The full loaded set in reverse-chronological order; older pages stream in
  // via the infinite-scroll sentinel below. Search results bypass pagination.
  const list = trimmed ? (results ?? []) : memos;
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
            className={segmentedItemClass(filter === "active")}
            aria-current={filter === "active" ? "page" : undefined}
          >
            未归档
          </Link>
          <Link
            to="/timeline?filter=archived"
            className={segmentedItemClass(filter === "archived")}
            aria-current={filter === "archived" ? "page" : undefined}
          >
            <Archive className="h-4 w-4" aria-hidden="true" />
            已归档
          </Link>
          <Link
            to="/timeline?filter=favorite"
            className={segmentedItemClass(filter === "favorite")}
            aria-current={filter === "favorite" ? "page" : undefined}
          >
            <Star className="h-4 w-4" aria-hidden="true" />
            收藏
          </Link>
        </fieldset>
        <p
          className="text-gray-500 text-xs dark:text-gray-400"
          aria-live="polite"
        >
          {searching
            ? "正在搜索"
            : trimmed
              ? searchError
                ? list.length > 0
                  ? `保留 ${list.length} 条上次结果`
                  : "搜索失败"
                : `找到 ${list.length} 条记录`
              : listError && list.length === 0
                ? "记录读取失败"
                : `${list.length} 条${filter === "active" ? "未归档" : filter === "archived" ? "已归档" : "收藏"}记录`}
        </p>
      </div>

      {filter === "active" && !trimmed && memories.length > 0 ? (
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
        ) : groups.length === 0 && !listError && !searchError ? (
          <div className={emptyStateClass}>
            {trimmed
              ? "没有匹配的记录。换一个词试试。"
              : filter === "archived"
                ? "还没有归档记录。"
                : filter === "favorite"
                  ? "还没有收藏记录。"
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
                    {formatEntryDate(group.date, today)}
                  </h2>
                  <span className="text-gray-400 text-xs dark:text-gray-500">
                    {group.entries.length} 条
                  </span>
                </div>
                <ul className="divide-y divide-gray-200/70 rounded-lg border border-gray-200/80 bg-white/70 p-1 shadow-sm shadow-gray-900/[0.02] dark:divide-gray-800 dark:border-gray-800 dark:bg-gray-900/45">
                  {group.entries.map((memo) => (
                    <li key={memo.id}>
                      <EntryCard memo={memo} openOnCardClick grouped />
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
  date: string;
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
  const chronological = chronologicalMemos(memos);
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
  return dateGroups;
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
            cause instanceof Error ? cause.message : "读取全部记录失败",
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
          : "正在读取全部记录…"}
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
          重新加载全部记录
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
