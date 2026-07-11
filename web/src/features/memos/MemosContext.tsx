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
import {
  createMemo as apiCreate,
  deleteMemo as apiDelete,
  getMemo as apiGetMemo,
  searchMemos as apiSearch,
  setMemoArchived as apiSetArchived,
  setMemoFavorited as apiSetFavorited,
  generateMemoSummary as apiSummary,
  updateMemo as apiUpdate,
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
  refresh: () => Promise<void>;
  loadMore: () => Promise<boolean>;
  loadAll: () => Promise<void>;
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
  const [memos, setMemos] = useState<Memo[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState("");
  const [summaries, setSummaries] = useState<Record<string, MemoAI>>({});
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

  const refresh = useCallback(async () => {
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
        setMemos((current) =>
          mergeMemos(
            current.filter((memo) => !isActive(memo)),
            res.memos,
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
      setError(err instanceof Error ? err.message : "读取记录失败");
    } finally {
      if (seq === refreshSeq.current) {
        setLoading(false);
      }
    }
  }, [token]);

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
          setMemos((current) => mergeMemos(current, res.memos));
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
          cause instanceof Error ? cause : new Error("读取更多记录失败");
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
  }, [token]);

  const loadAll = useCallback(async () => {
    const seenCursors = new Set<string>();
    while (cursorRef.current) {
      const cursor = cursorRef.current;
      if (seenCursors.has(cursor)) {
        throw new Error("分页游标未前进，请重新加载");
      }
      seenCursors.add(cursor);
      await loadMore();
    }
  }, [loadMore]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const apply = useCallback((memo: Memo) => {
    cacheGenerationRef.current += 1;
    setMemos((current) => sortMemos(upsertMemo(current, memo)));
    return memo;
  }, []);

  const getById = useCallback(
    (id: string) => memos.find((memo) => memo.id === id),
    [memos],
  );

  const fetchMemo = useCallback(
    async (id: string) => {
      // Always hit the detail endpoint: it returns the freshest memo plus any
      // stored summary, which the list payload omits. Cache both.
      const res = await apiGetMemo(token, id);
      if (res.ai) {
        const ai = res.ai;
        setSummaries((current) => ({ ...current, [id]: ai }));
      }
      return apply(res.memo);
    },
    [token, apply],
  );

  const create = useCallback(
    async (input: { content: string; entryDate: string }) => {
      const res = await apiCreate(token, input);
      return apply(res.memo);
    },
    [token, apply],
  );

  const update = useCallback(
    async (memo: Memo, input: { content?: string; entryDate?: string }) => {
      const res = await apiUpdate(token, memo, input);
      return apply(res.memo);
    },
    [token, apply],
  );

  const setFavorited = useCallback(
    async (memo: Memo, favorited: boolean) => {
      const res = await apiSetFavorited(token, memo, favorited);
      return apply(res.memo);
    },
    [token, apply],
  );

  const setArchived = useCallback(
    async (memo: Memo, archived: boolean) => {
      const res = await apiSetArchived(token, memo, archived);
      return apply(res.memo);
    },
    [token, apply],
  );

  const remove = useCallback(
    async (memo: Memo) => {
      await apiDelete(token, memo);
      cacheGenerationRef.current += 1;
      setMemos((current) => current.filter((item) => item.id !== memo.id));
    },
    [token],
  );

  const summarize = useCallback(
    async (memo: Memo) => {
      const res = await apiSummary(token, memo);
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
      refresh,
      loadMore,
      loadAll,
      getById,
      fetchMemo,
      create,
      update,
      setFavorited,
      setArchived,
      remove,
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
      refresh,
      loadMore,
      loadAll,
      getById,
      fetchMemo,
      create,
      update,
      setFavorited,
      setArchived,
      remove,
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
