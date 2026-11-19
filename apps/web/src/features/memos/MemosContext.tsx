import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useI18n } from "../../i18n/I18nProvider";
import {
  ApiError,
  createMemo as apiCreate,
  deleteMemo as apiDelete,
  getMemo as apiGetMemo,
  purgeMemo as apiPurge,
  restoreMemo as apiRestore,
  searchMemos as apiSearch,
  setMemoArchived as apiSetArchived,
  setMemoFavorited as apiSetFavorited,
  generateMemoSummary as apiSummary,
  updateMemo as apiUpdate,
  getMemoCalendarCounts,
  listMemos,
  type Memo,
  type MemoAI,
  type MemoListOptions,
  uploadAttachment,
} from "../../lib/api";
import { isActive, mergeMemos, sortMemos, upsertMemo } from "./memos";

const PAGE_SIZE = 200;
const ACTIVE_LIST_OPTIONS: MemoListOptions = {
  archived: false,
  favorited: false,
};

export interface UploadedAttachment {
  url: string;
  filename: string;
  isImage: boolean;
}

interface MemosContextValue {
  memos: Memo[];
  loading: boolean;
  loadingMore: boolean;
  hasMore: boolean;
  error: string;
  summaries: Record<string, MemoAI>;
  refreshIfNeeded: () => void;
  refresh: () => Promise<void>;
  loadMore: () => Promise<boolean>;
  calendarRevision: number;
  calendarCounts: (
    month: string,
    signal: AbortSignal,
  ) => Promise<Record<string, number>>;
  calendarDay: (
    entryDate: string,
    cursor: string | undefined,
    signal: AbortSignal,
  ) => Promise<{ memos: Memo[]; nextCursor?: string }>;
  getById: (id: string) => Memo | undefined;
  fetchMemo: (id: string) => Promise<Memo>;
  create: (input: { content: string; entryDate: string }) => Promise<Memo>;
  update: (
    memo: Memo,
    input: { content?: string; entryDate?: string },
  ) => Promise<Memo>;
  setFavorited: (memo: Memo, favorited: boolean) => Promise<Memo>;
  setArchived: (memo: Memo, archived: boolean) => Promise<Memo>;
  remove: (memo: Memo) => Promise<void>;
  restore: (memo: Memo) => Promise<Memo>;
  purge: (memo: Memo) => Promise<void>;
  summarize: (memo: Memo) => Promise<MemoAI>;
  listPage: (
    options: MemoListOptions,
    cursor?: string,
  ) => Promise<{ memos: Memo[]; nextCursor?: string }>;
  search: (query: string, options?: MemoListOptions) => Promise<Memo[]>;
  upload: (file: File) => Promise<UploadedAttachment>;
}

const MemosContext = createContext<MemosContextValue | null>(null);

export function MemosProvider({
  token,
  children,
}: {
  token: string;
  children: ReactNode;
}) {
  const { locale, t } = useI18n();
  const [memos, setMemos] = useState<Memo[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState("");
  const [summaries, setSummaries] = useState<Record<string, MemoAI>>({});
  const [calendarRevision, setCalendarRevision] = useState(0);
  const activeListTokenRef = useRef<string>(undefined);
  // Monotonic id so a slow earlier refresh can't overwrite a newer one
  // (e.g. StrictMode's double-invoke, or a manual refresh racing the mount).
  const refreshSeq = useRef(0);
  // Any canonical detail or mutation response advances this generation. A list
  // request that started before it must retry instead of replacing newer state.
  const cacheGenerationRef = useRef(0);
  // Opaque cursor for the next (older) page; empty once the list is exhausted.
  const cursorRef = useRef("");
  const loadMoreRequestRef = useRef<{
    id: number;
    cursor: string;
    refreshSeq: number;
    cacheGeneration: number;
    promise: Promise<boolean>;
  } | null>(null);
  const loadMoreRequestSeq = useRef(0);
  const detailRequestSeqRef = useRef(new Map<string, number>());
  const summaryRequestSeqRef = useRef(new Map<string, number>());
  const memoGenerationRef = useRef(new Map<string, number>());
  const memoSnapshotsRef = useRef(new Map<string, Memo>());

  const rememberSnapshot = useCallback((memo: Memo) => {
    const current = memoSnapshotsRef.current.get(memo.id);
    if (current && current.version > memo.version) {
      return current;
    }
    memoSnapshotsRef.current.set(memo.id, memo);
    return memo;
  }, []);

  useEffect(() => {
    void locale;
    setError((current) => (current ? t("records.loadFailed") : current));
  }, [locale, t]);

  const refresh = useCallback(async () => {
    activeListTokenRef.current = token;
    const seq = ++refreshSeq.current;
    loadMoreRequestRef.current = null;
    cursorRef.current = "";
    setLoading(true);
    setLoadingMore(false);
    setHasMore(false);
    try {
      while (seq === refreshSeq.current) {
        const cacheGeneration = cacheGenerationRef.current;
        const res = await listMemos(
          token,
          PAGE_SIZE,
          undefined,
          ACTIVE_LIST_OPTIONS,
        );
        if (seq !== refreshSeq.current) {
          return;
        }
        if (cacheGeneration !== cacheGenerationRef.current) {
          continue;
        }
        const freshMemos = res.memos.map(rememberSnapshot);
        setMemos((current) =>
          mergeMemos(
            current.filter((memo) => !isActive(memo)),
            freshMemos,
          ),
        );
        cursorRef.current = res.nextCursor ?? "";
        setHasMore(Boolean(res.nextCursor));
        setError("");
        return;
      }
    } catch (err) {
      if (seq !== refreshSeq.current) {
        return;
      }
      const message =
        err instanceof Error ? err.message : t("records.loadFailed");
      setError(message);
    } finally {
      if (seq === refreshSeq.current) {
        setLoading(false);
      }
    }
  }, [token, t, rememberSnapshot]);

  // Appends the next older page. A concurrent refresh (newer seq) discards the
  // result so pages from a stale list never leak into a fresh one.
  const loadMore = useCallback(async () => {
    const cursor = cursorRef.current;
    if (!cursor) {
      return false;
    }
    if (loadMoreRequestRef.current) {
      return loadMoreRequestRef.current.promise;
    }
    const request = {
      id: ++loadMoreRequestSeq.current,
      cursor,
      refreshSeq: refreshSeq.current,
      cacheGeneration: cacheGenerationRef.current,
      promise: Promise.resolve(false),
    };
    loadMoreRequestRef.current = request;
    request.promise = (async () => {
      setLoadingMore(true);
      try {
        while (loadMoreRequestRef.current?.id === request.id) {
          const res = await listMemos(
            token,
            PAGE_SIZE,
            cursor,
            ACTIVE_LIST_OPTIONS,
          );
          if (
            loadMoreRequestRef.current?.id !== request.id ||
            request.refreshSeq !== refreshSeq.current ||
            cursorRef.current !== request.cursor
          ) {
            return Boolean(cursorRef.current);
          }
          if (request.cacheGeneration !== cacheGenerationRef.current) {
            request.cacheGeneration = cacheGenerationRef.current;
            continue;
          }
          const freshMemos = res.memos.map(rememberSnapshot);
          setMemos((current) => mergeMemos(current, freshMemos));
          cursorRef.current = res.nextCursor ?? "";
          setHasMore(Boolean(res.nextCursor));
          setError("");
          return Boolean(res.nextCursor);
        }
        return Boolean(cursorRef.current);
      } catch (cause) {
        if (
          loadMoreRequestRef.current?.id !== request.id ||
          request.refreshSeq !== refreshSeq.current
        ) {
          return Boolean(cursorRef.current);
        }
        const error =
          cause instanceof Error
            ? cause
            : new Error(t("records.loadMoreFailed"));
        setError(error.message);
        throw error;
      } finally {
        if (loadMoreRequestRef.current?.id === request.id) {
          loadMoreRequestRef.current = null;
          setLoadingMore(false);
        }
      }
    })();
    return request.promise;
  }, [token, t, rememberSnapshot]);

  const refreshIfNeeded = useCallback(() => {
    if (activeListTokenRef.current !== token) {
      void refresh();
    }
  }, [refresh, token]);

  const apply = useCallback(
    (memo: Memo) => {
      const current = rememberSnapshot(memo);
      if (current !== memo) {
        return current;
      }
      cacheGenerationRef.current += 1;
      memoGenerationRef.current.set(
        memo.id,
        (memoGenerationRef.current.get(memo.id) ?? 0) + 1,
      );
      setMemos((existing) => sortMemos(upsertMemo(existing, memo)));
      return memo;
    },
    [rememberSnapshot],
  );

  const applyWrite = useCallback(
    (memo: Memo) => {
      setCalendarRevision((current) => current + 1);
      return apply(memo);
    },
    [apply],
  );

  const getById = useCallback(
    (id: string) => memos.find((memo) => memo.id === id),
    [memos],
  );

  const fetchMemo = useCallback(
    async (id: string) => {
      // Always hit the detail endpoint: it returns the freshest memo plus any
      // stored summary, which the list payload omits. Cache both.
      const request = (detailRequestSeqRef.current.get(id) ?? 0) + 1;
      detailRequestSeqRef.current.set(id, request);
      const generation = memoGenerationRef.current.get(id) ?? 0;
      const res = await apiGetMemo(token, id);
      const current = memoSnapshotsRef.current.get(id);
      if (
        detailRequestSeqRef.current.get(id) !== request ||
        (memoGenerationRef.current.get(id) ?? 0) !== generation ||
        (current && current.version > res.memo.version)
      ) {
        // Preserve canonical writes and newer reads, including deletion. The
        // caller may still await this request after leaving and returning.
        if (current?.deletedAt) {
          throw new ApiError(t("entry.missing"), 404, "not_found");
        }
        return current ?? res.memo;
      }
      setSummaries((existing) => {
        if (res.ai) {
          return { ...existing, [id]: res.ai };
        }
        const { [id]: _removed, ...rest } = existing;
        return rest;
      });
      return apply(res.memo);
    },
    [token, apply, t],
  );

  const create = useCallback(
    async (input: { content: string; entryDate: string }) => {
      const res = await apiCreate(token, input);
      return applyWrite(res.memo);
    },
    [token, applyWrite],
  );

  const update = useCallback(
    async (memo: Memo, input: { content?: string; entryDate?: string }) => {
      const res = await apiUpdate(token, memo, input);
      // A content edit makes the cached AI summary describe stale text; drop
      // it so the detail page offers to regenerate instead of misleading.
      if (input.content !== undefined && input.content !== memo.content) {
        setSummaries((current) => {
          if (!(memo.id in current)) {
            return current;
          }
          const { [memo.id]: _removed, ...rest } = current;
          return rest;
        });
      }
      return applyWrite(res.memo);
    },
    [token, applyWrite],
  );

  const setFavorited = useCallback(
    async (memo: Memo, favorited: boolean) => {
      const res = await apiSetFavorited(token, memo, favorited);
      return applyWrite(res.memo);
    },
    [token, applyWrite],
  );

  const setArchived = useCallback(
    async (memo: Memo, archived: boolean) => {
      const res = await apiSetArchived(token, memo, archived);
      return applyWrite(res.memo);
    },
    [token, applyWrite],
  );

  const remove = useCallback(
    async (memo: Memo) => {
      const res = await apiDelete(token, memo);
      rememberSnapshot(res.memo);
      cacheGenerationRef.current += 1;
      memoGenerationRef.current.set(
        memo.id,
        (memoGenerationRef.current.get(memo.id) ?? 0) + 1,
      );
      setMemos((current) => current.filter((item) => item.id !== memo.id));
      setCalendarRevision((current) => current + 1);
    },
    [token, rememberSnapshot],
  );

  const restore = useCallback(
    async (memo: Memo) => {
      const res = await apiRestore(token, memo);
      return applyWrite(res.memo);
    },
    [token, applyWrite],
  );

  const purge = useCallback(
    async (memo: Memo) => {
      const res = await apiPurge(token, memo);
      rememberSnapshot(res.memo);
      cacheGenerationRef.current += 1;
      memoGenerationRef.current.set(
        memo.id,
        (memoGenerationRef.current.get(memo.id) ?? 0) + 1,
      );
      setMemos((current) => current.filter((item) => item.id !== memo.id));
      setSummaries((current) => {
        if (!(memo.id in current)) {
          return current;
        }
        const { [memo.id]: _removed, ...rest } = current;
        return rest;
      });
    },
    [token, rememberSnapshot],
  );

  const summarize = useCallback(
    async (memo: Memo) => {
      const request = (summaryRequestSeqRef.current.get(memo.id) ?? 0) + 1;
      summaryRequestSeqRef.current.set(memo.id, request);
      const res = await apiSummary(token, memo);
      const current = memoSnapshotsRef.current.get(memo.id);
      if (
        summaryRequestSeqRef.current.get(memo.id) !== request ||
        current?.deletedAt ||
        (current && current.version !== memo.version)
      ) {
        return res.ai;
      }
      // A same-version detail read can finish while generation is pending;
      // the new summary still wins. Reads started before this canonical result
      // must not replace it with their earlier summary snapshot.
      memoGenerationRef.current.set(
        memo.id,
        (memoGenerationRef.current.get(memo.id) ?? 0) + 1,
      );
      setSummaries((current) => ({ ...current, [memo.id]: res.ai }));
      return res.ai;
    },
    [token],
  );

  const listPage = useCallback(
    async (options: MemoListOptions, cursor?: string) => {
      const res = await listMemos(token, PAGE_SIZE, cursor, options);
      return { ...res, memos: sortMemos(res.memos) };
    },
    [token],
  );

  const calendarCounts = useCallback(
    async (month: string, signal: AbortSignal) => {
      const result = await getMemoCalendarCounts(token, month, signal);
      return result.counts;
    },
    [token],
  );

  const calendarDay = useCallback(
    (entryDate: string, cursor: string | undefined, signal: AbortSignal) =>
      listMemos(
        token,
        50,
        cursor,
        { ...ACTIVE_LIST_OPTIONS, entryDate },
        signal,
      ),
    [token],
  );

  // Server-side FTS search. Results are returned to the caller, not merged into
  // the main list, so a search view never disturbs the cached timeline.
  const search = useCallback(
    async (query: string, options: MemoListOptions = {}) => {
      const res = await apiSearch(token, query, 100, options);
      return sortMemos(res.memos);
    },
    [token],
  );

  const upload = useCallback(
    async (file: File): Promise<UploadedAttachment> => {
      const res = await uploadAttachment(token, file);
      return {
        url: res.attachment.url,
        filename: res.attachment.filename,
        isImage: res.attachment.contentType.startsWith("image/"),
      };
    },
    [token],
  );

  const value = useMemo<MemosContextValue>(
    () => ({
      memos,
      loading,
      loadingMore,
      hasMore,
      error,
      summaries,
      refreshIfNeeded,
      refresh,
      loadMore,
      calendarRevision,
      calendarCounts,
      calendarDay,
      getById,
      fetchMemo,
      create,
      update,
      setFavorited,
      setArchived,
      remove,
      restore,
      purge,
      summarize,
      listPage,
      search,
      upload,
    }),
    [
      memos,
      loading,
      loadingMore,
      hasMore,
      error,
      summaries,
      refreshIfNeeded,
      refresh,
      loadMore,
      calendarRevision,
      calendarCounts,
      calendarDay,
      getById,
      fetchMemo,
      create,
      update,
      setFavorited,
      setArchived,
      remove,
      restore,
      purge,
      summarize,
      listPage,
      search,
      upload,
    ],
  );

  return (
    <MemosContext.Provider value={value}>{children}</MemosContext.Provider>
  );
}

export function useMemos(): MemosContextValue {
  const value = useContext(MemosContext);
  if (!value) {
    throw new Error("useMemos must be used within a MemosProvider");
  }
  return value;
}

/** The home and active timeline share a list cache, loaded only on demand. */
export function useActiveMemos(enabled = true): MemosContextValue {
  const context = useMemos();
  const { refreshIfNeeded } = context;
  useEffect(() => {
    if (enabled) {
      refreshIfNeeded();
    }
  }, [enabled, refreshIfNeeded]);
  return context;
}
