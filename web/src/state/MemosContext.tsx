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
  setMemoPinned as apiSetPinned,
  generateMemoSummary as apiSummary,
  updateMemo as apiUpdate,
  listMemos,
  type Memo,
  type MemoAI,
  uploadAttachment,
} from "../lib/api";
import { mergeMemos, sortMemos, upsertMemo } from "../lib/memos";

const PAGE_SIZE = 200;

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
  loadMore: () => Promise<void>;
  getById: (id: string) => Memo | undefined;
  fetchMemo: (id: string) => Promise<Memo>;
  create: (input: { content: string; entryDate: string }) => Promise<Memo>;
  update: (
    memo: Memo,
    input: { content?: string; entryDate?: string },
  ) => Promise<Memo>;
  setPinned: (memo: Memo, pinned: boolean) => Promise<Memo>;
  setArchived: (memo: Memo, archived: boolean) => Promise<Memo>;
  remove: (memo: Memo) => Promise<void>;
  summarize: (memo: Memo) => Promise<MemoAI>;
  search: (query: string) => Promise<Memo[]>;
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
  // Opaque cursor for the next (older) page; empty once the list is exhausted.
  const cursorRef = useRef("");

  const refresh = useCallback(async () => {
    const seq = ++refreshSeq.current;
    setLoading(true);
    try {
      const res = await listMemos(token, PAGE_SIZE);
      if (seq !== refreshSeq.current) {
        return;
      }
      setMemos(sortMemos(res.memos));
      cursorRef.current = res.nextCursor ?? "";
      setHasMore(Boolean(res.nextCursor));
      setError("");
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
    if (!cursorRef.current) {
      return;
    }
    const seq = refreshSeq.current;
    const cursor = cursorRef.current;
    setLoadingMore(true);
    try {
      const res = await listMemos(token, PAGE_SIZE, cursor);
      if (seq !== refreshSeq.current) {
        return;
      }
      setMemos((current) => mergeMemos(current, res.memos));
      cursorRef.current = res.nextCursor ?? "";
      setHasMore(Boolean(res.nextCursor));
    } catch (err) {
      if (seq !== refreshSeq.current) {
        return;
      }
      setError(err instanceof Error ? err.message : "读取更多记录失败");
    } finally {
      if (seq === refreshSeq.current) {
        setLoadingMore(false);
      }
    }
  }, [token]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const apply = useCallback((memo: Memo) => {
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

  const setPinned = useCallback(
    async (memo: Memo, pinned: boolean) => {
      const res = await apiSetPinned(token, memo, pinned);
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

  // Server-side FTS search. Results are returned to the caller, not merged into
  // the main list, so a search view never disturbs the cached timeline.
  const search = useCallback(
    async (query: string) => {
      const res = await apiSearch(token, query);
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
      getById,
      fetchMemo,
      create,
      update,
      setPinned,
      setArchived,
      remove,
      summarize,
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
      getById,
      fetchMemo,
      create,
      update,
      setPinned,
      setArchived,
      remove,
      summarize,
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
