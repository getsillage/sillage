import {
  Archive,
  ArrowLeft,
  LoaderCircle,
  Pencil,
  Sparkles,
  Star,
  Trash2,
  X,
} from "lucide-react";
import type { KeyboardEvent as ReactKeyboardEvent } from "react";
import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { EntryComposer } from "../components/EntryComposer";
import { LocalDateTime } from "../components/LocalDateTime";
import { Markdown } from "../components/Markdown";
import {
  dangerButtonClass,
  ghostLinkClass,
  iconButtonClass,
  mutedTextClass,
  pageTitleClass,
  readingShellClass,
  secondaryButtonClass,
  skeletonClass,
  subtleButtonClass,
} from "../components/ui";
import { ApiError, type Memo, type MemoAI } from "../lib/api";
import { formatEntryDate, todayISO } from "../lib/date";
import { useMemos } from "../state/MemosContext";

/** Number of source memos a summary was grounded in (>=1). */
function summarySourceCount(ai: MemoAI): number {
  try {
    const ids = JSON.parse(ai.sourceMemoIds);
    return Array.isArray(ids) && ids.length > 0 ? ids.length : 1;
  } catch {
    return 1;
  }
}

function detailReturnTarget(state: unknown): string {
  const candidate = (state as { returnTo?: unknown } | null)?.returnTo;
  return typeof candidate === "string" && /^\/(?!\/)/.test(candidate)
    ? candidate
    : "/timeline";
}

function detailMemoSnapshot(state: unknown, id: string): Memo | undefined {
  const candidate = (state as { memoSnapshot?: unknown } | null)?.memoSnapshot;
  if (!candidate || typeof candidate !== "object") {
    return undefined;
  }
  const snapshot = candidate as Partial<Memo>;
  const isNullableString = (value: unknown) =>
    value === null || typeof value === "string";
  if (
    snapshot.id !== id ||
    typeof snapshot.content !== "string" ||
    typeof snapshot.entryDate !== "string" ||
    typeof snapshot.version !== "number" ||
    !isNullableString(snapshot.favoritedAt) ||
    !isNullableString(snapshot.archivedAt) ||
    typeof snapshot.createdAt !== "string" ||
    typeof snapshot.updatedAt !== "string" ||
    !isNullableString(snapshot.deletedAt)
  ) {
    return undefined;
  }
  return snapshot as Memo;
}

function detailReturnLabel(target: string): string {
  if (target.startsWith("/ask")) {
    return "问答";
  }
  if (target === "/" || target.startsWith("/?")) {
    return "写记录";
  }
  return "全部记录";
}

export function EntryPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const returnTarget = detailReturnTarget(location.state);
  const returnLabel = detailReturnLabel(returnTarget);
  const memos = useMemos();
  const { fetchMemo } = memos;
  const cachedMemo = memos.getById(id);
  const routeSnapshot = detailMemoSnapshot(location.state, id);
  const memo =
    routeSnapshot && (!cachedMemo || routeSnapshot.version > cachedMemo.version)
      ? routeSnapshot
      : cachedMemo;
  const summary = memos.summaries[id];
  const [editorMemo, setEditorMemo] = useState<Memo | null>(null);
  const [detailAttempt, setDetailAttempt] = useState(0);
  const [detailState, setDetailState] = useState<{
    id: string;
    status: "loading" | "ready" | "missing" | "error";
    error: string;
  }>({ id: "", status: "loading", error: "" });
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const deleteTriggerRef = useRef<HTMLButtonElement>(null);
  const restoreDeleteFocusRef = useRef(false);
  const actionPendingRef = useRef(false);

  useEffect(() => {
    if (!confirmingDelete && restoreDeleteFocusRef.current) {
      restoreDeleteFocusRef.current = false;
      deleteTriggerRef.current?.focus();
    }
  }, [confirmingDelete]);

  // Load fresh memo + stored summary on navigation and explicit retry. The
  // attempt token intentionally re-runs the same request after transient errors.
  // biome-ignore lint/correctness/useExhaustiveDependencies: detailAttempt is the explicit retry trigger
  useEffect(() => {
    if (!id) {
      return;
    }
    let cancelled = false;
    setEditorMemo(null);
    setConfirmingDelete(false);
    setDetailState({ id, status: "loading", error: "" });
    fetchMemo(id)
      .then(() => {
        if (!cancelled) {
          setDetailState({ id, status: "ready", error: "" });
        }
      })
      .catch((cause) => {
        if (cancelled) {
          return;
        }
        if (cause instanceof ApiError && cause.status === 404) {
          setDetailState({ id, status: "missing", error: "" });
          return;
        }
        setDetailState({
          id,
          status: "error",
          error: cause instanceof Error ? cause.message : "读取记录失败",
        });
      });
    return () => {
      cancelled = true;
    };
  }, [id, fetchMemo, detailAttempt]);

  const currentDetailState =
    detailState.id === id
      ? detailState
      : { id, status: "loading" as const, error: "" };
  const detailReady = currentDetailState.status === "ready";

  if (currentDetailState.status === "missing") {
    return (
      <main className={readingShellClass}>
        <p className={mutedTextClass}>这条记录不存在或已被删除。</p>
        <Link
          to={returnTarget}
          className={`${ghostLinkClass} mt-3 inline-flex h-10 items-center gap-2 px-2 text-sm`}
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          回到{returnLabel}
        </Link>
      </main>
    );
  }

  if (!memo) {
    if (currentDetailState.status === "error") {
      return (
        <main className={readingShellClass}>
          <h1 className={pageTitleClass}>暂时无法打开记录</h1>
          <p
            role="alert"
            className="mt-2 text-red-600 text-sm dark:text-red-400"
          >
            {currentDetailState.error}
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            <button
              type="button"
              className={secondaryButtonClass}
              onClick={() => setDetailAttempt((current) => current + 1)}
            >
              重新加载
            </button>
            <Link
              to={returnTarget}
              className={`${ghostLinkClass} inline-flex h-10 items-center gap-2 px-2 text-sm`}
            >
              <ArrowLeft className="h-4 w-4" aria-hidden="true" />
              回到{returnLabel}
            </Link>
          </div>
        </main>
      );
    }
    return (
      <main className={readingShellClass}>
        <div className="space-y-5" role="status">
          <span className="sr-only">正在打开记录</span>
          <div className={`${skeletonClass} h-4 w-32`} />
          <div className={`${skeletonClass} h-40 w-full`} />
        </div>
      </main>
    );
  }

  if (editorMemo) {
    return (
      <main className={readingShellClass}>
        <div className="mb-6">
          <h1 className={pageTitleClass}>编辑记录</h1>
        </div>
        <section aria-label="编辑记录正文">
          <EntryComposer
            key={`memo:${editorMemo.id}`}
            draftKey={`memo:${editorMemo.id}`}
            mode="edit"
            submitLabel="更新"
            initialContent={editorMemo.content}
            initialEntryDate={editorMemo.entryDate}
            initialVersion={editorMemo.version}
            onUpload={memos.upload}
            onSubmit={async (input) => {
              await memos.update(editorMemo, input);
              setEditorMemo(null);
            }}
            onCancel={() => setEditorMemo(null)}
          />
        </section>
      </main>
    );
  }

  async function runAction(action: "favorite" | "archive" | "delete") {
    if (!memo || !detailReady || actionPendingRef.current) {
      return;
    }
    actionPendingRef.current = true;
    setBusy(action);
    setError("");
    setConfirmingDelete(false);
    try {
      if (action === "favorite") {
        await memos.setFavorited(memo, !memo.favoritedAt);
      } else if (action === "archive") {
        await memos.setArchived(memo, !memo.archivedAt);
      } else {
        await memos.remove(memo);
        navigate("/");
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "操作失败");
    } finally {
      actionPendingRef.current = false;
      setBusy("");
    }
  }

  async function summarize() {
    if (!memo || !detailReady || actionPendingRef.current) {
      return;
    }
    actionPendingRef.current = true;
    setBusy("summarize");
    setError("");
    try {
      await memos.summarize(memo);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "生成总结失败");
    } finally {
      actionPendingRef.current = false;
      setBusy("");
    }
  }

  return (
    <main className={readingShellClass}>
      {currentDetailState.status === "loading" ? (
        <p
          role="status"
          className="mb-3 inline-flex items-center gap-2 text-gray-500 text-sm dark:text-gray-400"
        >
          <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
          正在获取最新内容
        </p>
      ) : currentDetailState.status === "error" ? (
        <div
          role="alert"
          className="mb-4 flex flex-wrap items-center gap-3 rounded-lg border border-red-200 bg-red-50/70 p-3 text-red-700 text-sm dark:border-red-900/60 dark:bg-red-950/25 dark:text-red-300"
        >
          <span className="min-w-0 flex-1">
            最新内容读取失败：{currentDetailState.error}
          </span>
          <button
            type="button"
            className={subtleButtonClass}
            onClick={() => setDetailAttempt((current) => current + 1)}
          >
            重新加载
          </button>
        </div>
      ) : null}
      <div className="mb-5 flex items-center justify-between gap-3 sm:mb-6">
        <Link
          to={returnTarget}
          className={`${ghostLinkClass} inline-flex h-10 items-center gap-2 px-2 text-sm`}
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          {returnLabel}
        </Link>
        <div className="flex shrink-0 items-center gap-1">
          <button
            type="button"
            onClick={() => runAction("favorite")}
            disabled={Boolean(busy) || !detailReady}
            className={iconButtonClass}
            aria-label={memo.favoritedAt ? "取消收藏" : "收藏"}
            title={memo.favoritedAt ? "取消收藏" : "收藏"}
          >
            {busy === "favorite" ? (
              <LoaderCircle className="h-4 w-4 animate-spin" />
            ) : (
              <Star
                className={`h-4 w-4 ${memo.favoritedAt ? "fill-current" : ""}`}
              />
            )}
          </button>
          <button
            type="button"
            onClick={() => runAction("archive")}
            disabled={Boolean(busy) || !detailReady}
            className={iconButtonClass}
            aria-label={memo.archivedAt ? "取消归档" : "归档"}
            title={memo.archivedAt ? "取消归档" : "归档"}
          >
            {busy === "archive" ? (
              <LoaderCircle className="h-4 w-4 animate-spin" />
            ) : (
              <Archive className="h-4 w-4" />
            )}
          </button>
          <button
            type="button"
            onClick={() => setEditorMemo(memo)}
            disabled={Boolean(busy) || !detailReady}
            className={iconButtonClass}
            aria-label="编辑"
            title="编辑"
          >
            <Pencil className="h-4 w-4" />
          </button>
          <button
            ref={deleteTriggerRef}
            type="button"
            onClick={() => {
              restoreDeleteFocusRef.current = false;
              setConfirmingDelete(true);
            }}
            disabled={Boolean(busy) || !detailReady}
            className={`${iconButtonClass} text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950/30 dark:hover:text-red-300`}
            aria-label="删除"
            title="删除"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      </div>

      <article className="min-w-0">
        <header className="flex flex-wrap items-center gap-2 border-gray-200/80 border-b pb-4 text-gray-500 text-sm dark:border-gray-800 dark:text-gray-400">
          <time dateTime={memo.entryDate}>
            {formatEntryDate(memo.entryDate, todayISO())}
          </time>
          {memo.favoritedAt ? (
            <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-800 dark:text-gray-300">
              <Star className="h-3 w-3 fill-current" aria-hidden="true" />
              已收藏
            </span>
          ) : null}
          {memo.archivedAt ? (
            <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-800 dark:text-gray-300">
              <Archive className="h-3 w-3" aria-hidden="true" />
              已归档
            </span>
          ) : null}
        </header>

        <div className="py-6 sm:py-7">
          {memo.content.trim() ? (
            <Markdown content={memo.content} />
          ) : (
            <p className="text-gray-400 dark:text-gray-500">空白记录</p>
          )}
        </div>
      </article>

      <section className="mt-6 border-gray-200/80 border-t pt-5 dark:border-gray-800">
        <div className="flex items-center justify-between gap-3">
          <h2 className="font-semibold text-gray-900 dark:text-gray-50">
            总结
          </h2>
          <button
            type="button"
            onClick={summarize}
            disabled={Boolean(busy) || !detailReady}
            className={subtleButtonClass}
          >
            <Sparkles className="h-4 w-4" />
            {busy === "summarize"
              ? "总结中…"
              : summary
                ? "重新总结"
                : "生成总结"}
          </button>
        </div>
        {summary ? (
          <div className="mt-3 rounded-lg bg-gray-100/70 p-4 dark:bg-gray-900/70">
            <Markdown
              content={summary.summary || "（暂无总结内容）"}
              variant="chat"
            />
            <p className={`mt-3 text-xs ${mutedTextClass}`}>
              基于 {summarySourceCount(summary)} 条记录生成
            </p>
          </div>
        ) : (
          <p className="mt-2 text-gray-400 text-sm dark:text-gray-500">
            让 AI 基于这条记录生成一段简短的总结。
          </p>
        )}
      </section>

      <section className="mt-5 border-gray-200/80 border-t pt-4 text-gray-500 text-sm dark:border-gray-800 dark:text-gray-400">
        <p>
          创建于 <LocalDateTime value={memo.createdAt} />
        </p>
        {memo.version > 1 ? (
          <p className="mt-1">
            最近修改 <LocalDateTime value={memo.updatedAt} />
            ，共修改 {memo.version - 1} 次
          </p>
        ) : null}
      </section>

      {error ? (
        <p role="alert" className="mt-4 text-red-600 text-sm dark:text-red-400">
          {error}
        </p>
      ) : null}

      {confirmingDelete ? (
        <DeleteDialog
          busy={busy === "delete"}
          onCancel={() => {
            restoreDeleteFocusRef.current = true;
            setConfirmingDelete(false);
          }}
          onConfirm={() => {
            restoreDeleteFocusRef.current = false;
            void runAction("delete");
          }}
        />
      ) : null}
    </main>
  );
}

function DeleteDialog({
  busy,
  onCancel,
  onConfirm,
}: {
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const cancelRef = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    cancelRef.current?.focus();
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !busy) {
        onCancel();
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [busy, onCancel]);

  function trapFocus(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key !== "Tab" || !dialogRef.current) {
      return;
    }
    const focusable = Array.from(
      dialogRef.current.querySelectorAll<HTMLButtonElement>(
        "button:not([disabled])",
      ),
    );
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center px-4">
      <button
        type="button"
        className="absolute inset-0 h-full w-full bg-gray-950/35 dark:bg-gray-950/65"
        aria-label="取消删除"
        tabIndex={-1}
        onClick={onCancel}
        disabled={busy}
      />
      <div
        ref={dialogRef}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="delete-title"
        aria-describedby="delete-description"
        onKeyDown={trapFocus}
        className="surface-enter relative w-full max-w-sm rounded-xl border border-gray-200 bg-white p-5 shadow-xl shadow-gray-950/15 dark:border-gray-700 dark:bg-gray-900 dark:shadow-black/30"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2
              id="delete-title"
              className="font-semibold text-gray-900 dark:text-gray-50"
            >
              删除这条记录？
            </h2>
            <p
              id="delete-description"
              className="mt-1 text-gray-500 text-sm leading-6 dark:text-gray-400"
            >
              删除后将无法在 Sillage 中恢复。
            </p>
          </div>
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            className={iconButtonClass}
            aria-label="关闭"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button
            ref={cancelRef}
            type="button"
            onClick={onCancel}
            disabled={busy}
            className={secondaryButtonClass}
          >
            取消
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy}
            className={`${dangerButtonClass} h-10 bg-red-600 px-4 text-white hover:bg-red-700 dark:bg-red-600 dark:text-white dark:hover:bg-red-500`}
          >
            {busy ? <LoaderCircle className="h-4 w-4 animate-spin" /> : null}
            {busy ? "删除中…" : "确认删除"}
          </button>
        </div>
      </div>
    </div>
  );
}
