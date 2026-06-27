import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import {
  createMemo as apiCreate,
  deleteMemo as apiDelete,
  getMemo as apiGetMemo,
  setMemoArchived as apiSetArchived,
  setMemoPinned as apiSetPinned,
  generateMemoSummary as apiSummary,
  updateMemo as apiUpdate,
  listMemos,
  type Memo,
  type MemoAI,
  uploadAttachment,
} from "../lib/api";
import { sortMemos, upsertMemo } from "../lib/memos";

export interface UploadedAttachment {
  url: string;
  filename: string;
  isImage: boolean;
}

interface MemosContextValue {
  memos: Memo[];
  loading: boolean;
  error: string;
  summaries: Record<string, MemoAI>;
  refresh: () => Promise<void>;
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
  const [error, setError] = useState("");
  const [summaries, setSummaries] = useState<Record<string, MemoAI>>({});

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listMemos(token);
      setMemos(sortMemos(res.memos));
      setError("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "读取记录失败");
    } finally {
      setLoading(false);
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
      const cached = memos.find((memo) => memo.id === id);
      if (cached) {
        return cached;
      }
      const res = await apiGetMemo(token, id);
      return apply(res.memo);
    },
    [memos, token, apply],
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
      error,
      summaries,
      refresh,
      getById,
      fetchMemo,
      create,
      update,
      setPinned,
      setArchived,
      remove,
      summarize,
      upload,
    }),
    [
      memos,
      loading,
      error,
      summaries,
      refresh,
      getById,
      fetchMemo,
      create,
      update,
      setPinned,
      setArchived,
      remove,
      summarize,
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
