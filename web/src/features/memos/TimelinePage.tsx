import { Archive, CalendarDays, List, Search, Star, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
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
} from "../../components/ui";
import { useI18n } from "../../i18n/I18nProvider";
import type { Memo, MemoListOptions } from "../../lib/api";
import {
  formatEntryDate,
  monthGrid,
  normalizeYearMonth,
  todayISO,
} from "../../lib/date";
import { CalendarView } from "./CalendarView";
import { EntryCard } from "./EntryCard";
import { useMemos } from "./MemosContext";
import {
  entriesByDate,
  entryDateCounts,
  isActive,
  mergeMemos,
  onThisDay,
} from "./memos";
import { OnThisDay } from "./OnThisDay";

type TimelineFilter = "active" | "archived" | "favorite";

type MemoSearchSnapshot = {
  query: string;
  memos: Memo[];
};

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
  const { t } = useI18n();
  return (
    <fieldset className={segmentedControlClass}>
      <legend className="sr-only">{t("timeline.viewLabel")}</legend>
      <Link
        to="/timeline"
        className={segmentedItemClass(!calendar)}
        aria-current={!calendar ? "page" : undefined}
      >
        <List className="h-4 w-4" aria-hidden="true" />
        {t("timeline.list")}
      </Link>
      <Link
        to="/timeline?view=calendar"
        className={segmentedItemClass(calendar)}
        aria-current={calendar ? "page" : undefined}
      >
        <CalendarDays className="h-4 w-4" aria-hidden="true" />
        {t("timeline.calendar")}
      </Link>
    </fieldset>
  );
}

export function TimelinePage() {
  const { t } = useI18n();
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
            <h1 className={pageTitleClass}>{t("timeline.title")}</h1>
            <p className={pageLeadClass}>{t("timeline.lead")}</p>
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
  const { locale, t } = useI18n();
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
  const feedbackLocaleRef = useRef(locale);

  useEffect(() => {
    if (feedbackLocaleRef.current === locale) {
      return;
    }
    feedbackLocaleRef.current = locale;
    setError((current) => (current ? t("records.loadFailed") : current));
  }, [locale, t]);

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
        const message =
          cause instanceof Error ? cause.message : t("records.loadFailed");
        setError(message);
      }
    } finally {
      if (request === requestSeqRef.current) {
        setLoading(false);
      }
    }
  }, [listPage, options, t]);

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
          cause instanceof Error
            ? cause
            : new Error(t("records.loadMoreFailed"));
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
  }, [listPage, options, t]);

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
  const { locale, t } = useI18n();
  const { search } = useMemos();
  const { memos, loading, error, refresh, loadMore, hasMore, loadingMore } =
    useTimelineMemoList(filter);
  const [query, setQuery] = useState("");
  const [searchSnapshot, setSearchSnapshot] =
    useState<MemoSearchSnapshot | null>(null);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState("");
  const [searchAttempt, setSearchAttempt] = useState(0);
  const searchFeedbackLocaleRef = useRef(locale);
  const trimmed = query.trim();
  const memories = onThisDay(memos, today);
  const options = useMemo(() => listOptionsFor(filter), [filter]);

  useEffect(() => {
    if (searchFeedbackLocaleRef.current === locale) {
      return;
    }
    searchFeedbackLocaleRef.current = locale;
    setSearchError((current) =>
      current ? t("timeline.searchFailed") : current,
    );
  }, [locale, t]);

  // Debounced server-side FTS search; empty query falls back to the recent list.
  useEffect(() => {
    void searchAttempt;
    if (!trimmed) {
      setSearchSnapshot(null);
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
            setSearchSnapshot({ query: trimmed, memos: found });
            setSearchError("");
          }
        })
        .catch((cause) => {
          if (!cancelled) {
            const message =
              cause instanceof Error
                ? cause.message
                : t("timeline.searchFailed");
            setSearchError(message);
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
  }, [trimmed, search, options, t, searchAttempt]);

  // The full loaded set in reverse-chronological order; older pages stream in
  // via the infinite-scroll sentinel below. Search results bypass pagination.
  const currentSearchResults =
    searchSnapshot?.query === trimmed ? searchSnapshot.memos : null;
  const list = trimmed ? (currentSearchResults ?? []) : memos;
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
          {t("timeline.search")}
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
          placeholder={t("timeline.searchPlaceholder")}
          className="block h-11 w-full rounded-lg border border-gray-200 bg-white/80 pr-11 pl-10 text-sm text-gray-900 transition-colors placeholder:text-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-300/55 dark:border-gray-700 dark:bg-gray-900/80 dark:text-gray-50 dark:placeholder:text-gray-500 dark:focus:border-gray-500 dark:focus:ring-gray-600/50"
        />
        {query ? (
          <button
            type="button"
            onClick={() => setQuery("")}
            className={`${iconButtonClass} absolute top-1/2 right-0.5 -translate-y-1/2`}
            aria-label={t("timeline.clearSearch")}
            title={t("timeline.clearSearch")}
          >
            <X className="h-4 w-4" />
          </button>
        ) : null}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <fieldset
          className={`${segmentedControlClass} flex min-w-0 w-full sm:w-auto`}
        >
          <legend className="sr-only">{t("timeline.statusLabel")}</legend>
          <Link
            to="/timeline"
            className={`${segmentedItemClass(filter === "active")} min-w-0 flex-1 px-2 sm:flex-none sm:px-3`}
            aria-current={filter === "active" ? "page" : undefined}
          >
            {t("timeline.active")}
          </Link>
          <Link
            to="/timeline?filter=archived"
            className={`${segmentedItemClass(filter === "archived")} min-w-0 flex-1 px-2 sm:flex-none sm:px-3`}
            aria-current={filter === "archived" ? "page" : undefined}
          >
            <Archive className="hidden h-4 w-4 sm:block" aria-hidden="true" />
            {t("timeline.archived")}
          </Link>
          <Link
            to="/timeline?filter=favorite"
            className={`${segmentedItemClass(filter === "favorite")} min-w-0 flex-1 px-2 sm:flex-none sm:px-3`}
            aria-current={filter === "favorite" ? "page" : undefined}
          >
            <Star className="hidden h-4 w-4 sm:block" aria-hidden="true" />
            {t("timeline.favorite")}
          </Link>
        </fieldset>
        <p
          className="text-gray-500 text-xs dark:text-gray-400"
          aria-live={searchError ? undefined : "polite"}
        >
          {searching
            ? t("timeline.searching")
            : trimmed
              ? searchError
                ? t("timeline.searchFailed")
                : t(
                    list.length === 1
                      ? "timeline.foundOne"
                      : "timeline.foundMany",
                    { count: list.length },
                  )
              : listError && list.length === 0
                ? t("timeline.loadFailed")
                : t(
                    filter === "active"
                      ? list.length === 1
                        ? "timeline.activeCountOne"
                        : "timeline.activeCountMany"
                      : filter === "archived"
                        ? list.length === 1
                          ? "timeline.archivedCountOne"
                          : "timeline.archivedCountMany"
                        : list.length === 1
                          ? "timeline.favoriteCountOne"
                          : "timeline.favoriteCountMany",
                    { count: list.length },
                  )}
        </p>
      </div>

      {filter === "active" && !trimmed && memories.length > 0 ? (
        <OnThisDay entries={memories} today={today} />
      ) : null}
      {searchError ? (
        <div className="flex flex-wrap items-center gap-3 rounded-lg border border-red-200 bg-red-50/70 p-3 text-red-700 text-sm dark:border-red-900/60 dark:bg-red-950/25 dark:text-red-300">
          <p role="alert" className="min-w-0 flex-1">
            {searchError}
          </p>
          <button
            type="button"
            className={secondaryButtonClass}
            disabled={searching}
            onClick={() => setSearchAttempt((current) => current + 1)}
          >
            {t(searching ? "timeline.searching" : "common.retry")}
          </button>
        </div>
      ) : null}
      <section className="min-w-0 pr-14 sm:pr-0">
        {(loading && !trimmed) || (searching && list.length === 0) ? (
          <TimelineSkeleton announce={!trimmed} />
        ) : groups.length === 0 && !listError && !searchError ? (
          <div className={emptyStateClass}>
            {trimmed
              ? t("timeline.noMatches")
              : filter === "archived"
                ? t("timeline.noArchived")
                : filter === "favorite"
                  ? t("timeline.noFavorites")
                  : t("timeline.noRecords")}
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
                    {formatEntryDate(group.date, today, locale)}
                  </h2>
                  <span className="text-gray-400 text-xs dark:text-gray-500">
                    {t(
                      group.entries.length === 1
                        ? "timeline.groupCountOne"
                        : "timeline.groupCountMany",
                      { count: group.entries.length },
                    )}
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
                ? t("timeline.retrying")
                : hasMore
                  ? t("timeline.retryMore")
                  : t("records.reload")}
            </button>
          </div>
        ) : showLoadMore ? (
          <div
            ref={sentinelRef}
            className="flex justify-center py-5 text-gray-500 text-sm dark:text-gray-400"
            aria-live="polite"
          >
            {loadingMore ? (
              t("timeline.loadingMore")
            ) : (
              <button
                type="button"
                onClick={() => void loadMore().catch(() => undefined)}
                className={ghostLinkClass}
              >
                {t("timeline.loadMore")}
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

function TimelineSkeleton({ announce = true }: { announce?: boolean }) {
  const { t } = useI18n();
  return (
    <div
      className="space-y-7"
      role={announce ? "status" : undefined}
      aria-hidden={announce ? undefined : true}
    >
      {announce ? (
        <span className="sr-only">{t("timeline.loadingRecords")}</span>
      ) : null}
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
  const { locale, t } = useI18n();
  const [fullLoadState, setFullLoadState] = useState<
    "loading" | "ready" | "error"
  >("loading");
  const [fullLoadError, setFullLoadError] = useState("");
  const fullLoadRequestRef = useRef(0);
  const feedbackLocaleRef = useRef(locale);

  useEffect(() => {
    if (feedbackLocaleRef.current === locale) {
      return;
    }
    feedbackLocaleRef.current = locale;
    setFullLoadError((current) =>
      current ? t("records.loadAllFailed") : current,
    );
  }, [locale, t]);

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
          const message =
            cause instanceof Error ? cause.message : t("records.loadAllFailed");
          setFullLoadError(message);
          setFullLoadState("error");
        }
      });
  }, [loadAll, t]);

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
          ? t("timeline.loadedContinuing", { count: memos.length })
          : t("timeline.loadingAll")}
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
          {t("timeline.reloadAll")}
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
