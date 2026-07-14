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
import { Markdown } from "../../components/Markdown";
import { useToast } from "../../components/Toast";
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
} from "../../components/ui";
import { useI18n } from "../../i18n/I18nProvider";
import type { TranslationKey } from "../../i18n/messages";
import { ApiError, type Memo, type MemoAI } from "../../lib/api";
import { formatEntryDate, todayISO } from "../../lib/date";
import { EntryComposer } from "./EntryComposer";
import { formatLocalDateTime } from "./LocalDateTime";
import { useMemos } from "./MemosContext";

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

function detailReturnLabelKey(target: string): TranslationKey {
  if (target.startsWith("/ask")) {
    return "ask.section";
  }
  if (target === "/" || target.startsWith("/?")) {
    return "nav.writeRecord";
  }
  return "nav.allRecords";
}

export function EntryPage() {
  const { locale, t } = useI18n();
  const toast = useToast();
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const returnTarget = detailReturnTarget(location.state);
  const returnLabel = t(detailReturnLabelKey(returnTarget));
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
  const feedbackLocaleRef = useRef(locale);

  useEffect(() => {
    if (feedbackLocaleRef.current === locale) {
      return;
    }
    feedbackLocaleRef.current = locale;
    setError("");
    setDetailState((current) =>
      current.status === "error"
        ? { ...current, error: t("records.loadFailed") }
        : current,
    );
  }, [locale, t]);

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
        const message =
          cause instanceof Error ? cause.message : t("records.loadFailed");
        setDetailState({
          id,
          status: "error",
          error: message,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [id, fetchMemo, detailAttempt, t]);

  const currentDetailState =
    detailState.id === id
      ? detailState
      : { id, status: "loading" as const, error: "" };
  const detailReady = currentDetailState.status === "ready";

  if (currentDetailState.status === "missing") {
    return (
      <main className={readingShellClass}>
        <p className={mutedTextClass}>{t("entry.missing")}</p>
        <Link
          to={returnTarget}
          className={`${ghostLinkClass} mt-3 inline-flex h-10 items-center gap-2 px-2 text-sm`}
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          {t("entry.backTo", { returnLabel })}
        </Link>
      </main>
    );
  }

  if (!memo) {
    if (currentDetailState.status === "error") {
      return (
        <main className={readingShellClass}>
          <h1 className={pageTitleClass}>{t("entry.openFailedTitle")}</h1>
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
              {t("common.reload")}
            </button>
            <Link
              to={returnTarget}
              className={`${ghostLinkClass} inline-flex h-10 items-center gap-2 px-2 text-sm`}
            >
              <ArrowLeft className="h-4 w-4" aria-hidden="true" />
              {t("entry.backTo", { returnLabel })}
            </Link>
          </div>
        </main>
      );
    }
    return (
      <main className={readingShellClass}>
        <div className="space-y-5" role="status">
          <span className="sr-only">{t("entry.opening")}</span>
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
          <h1 className={pageTitleClass}>{t("entry.editTitle")}</h1>
        </div>
        <section aria-label={t("entry.editBody")}>
          <EntryComposer
            key={`memo:${editorMemo.id}`}
            draftKey={`memo:${editorMemo.id}`}
            mode="edit"
            submitLabel={t("entry.update")}
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
    try {
      if (action === "favorite") {
        const favorited = !memo.favoritedAt;
        await memos.setFavorited(memo, favorited);
        toast.showToast({
          kind: "success",
          message: t(
            favorited ? "records.favoritedNotice" : "records.unfavoritedNotice",
          ),
        });
      } else if (action === "archive") {
        const archived = !memo.archivedAt;
        await memos.setArchived(memo, archived);
        toast.showToast({
          kind: "success",
          message: t(
            archived ? "records.archivedNotice" : "records.unarchivedNotice",
          ),
        });
      } else {
        await memos.remove(memo);
        toast.showToast({ kind: "success", message: t("records.deleted") });
        navigate(returnTarget, { replace: true });
      }
    } catch (cause) {
      const message =
        cause instanceof Error ? cause.message : t("entry.actionFailed");
      setError(message);
      toast.showToast({ kind: "error", message });
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
      toast.showToast({
        kind: "success",
        message: t("entry.summaryGenerated"),
      });
    } catch (cause) {
      const message =
        cause instanceof Error ? cause.message : t("entry.summaryFailed");
      setError(message);
      toast.showToast({ kind: "error", message });
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
          {t("entry.loadingLatest")}
        </p>
      ) : currentDetailState.status === "error" ? (
        <div
          role="alert"
          className="mb-4 flex flex-wrap items-center gap-3 rounded-lg border border-red-200 bg-red-50/70 p-3 text-red-700 text-sm dark:border-red-900/60 dark:bg-red-950/25 dark:text-red-300"
        >
          <span className="min-w-0 flex-1">
            {t("entry.latestFailed", { error: currentDetailState.error })}
          </span>
          <button
            type="button"
            className={subtleButtonClass}
            onClick={() => setDetailAttempt((current) => current + 1)}
          >
            {t("common.reload")}
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
            aria-label={t(
              memo.favoritedAt ? "records.unfavorite" : "records.favorite",
            )}
            title={t(
              memo.favoritedAt ? "records.unfavorite" : "records.favorite",
            )}
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
            aria-label={t(
              memo.archivedAt ? "records.unarchive" : "records.archive",
            )}
            title={t(memo.archivedAt ? "records.unarchive" : "records.archive")}
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
            aria-label={t("common.edit")}
            title={t("common.edit")}
          >
            <Pencil className="h-4 w-4" />
          </button>
          <button
            ref={deleteTriggerRef}
            type="button"
            onClick={() => {
              setError("");
              restoreDeleteFocusRef.current = false;
              setConfirmingDelete(true);
            }}
            disabled={Boolean(busy) || !detailReady}
            className={`${iconButtonClass} text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950/30 dark:hover:text-red-300`}
            aria-label={t("common.delete")}
            title={t("common.delete")}
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      </div>

      <article className="min-w-0">
        <header className="flex flex-wrap items-center gap-2 border-gray-200/80 border-b pb-4 text-gray-500 text-sm dark:border-gray-800 dark:text-gray-400">
          <time dateTime={memo.entryDate}>
            {formatEntryDate(memo.entryDate, todayISO(), locale)}
          </time>
          {memo.favoritedAt ? (
            <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-800 dark:text-gray-300">
              <Star className="h-3 w-3 fill-current" aria-hidden="true" />
              {t("records.favorited")}
            </span>
          ) : null}
          {memo.archivedAt ? (
            <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-800 dark:text-gray-300">
              <Archive className="h-3 w-3" aria-hidden="true" />
              {t("records.archived")}
            </span>
          ) : null}
        </header>

        <div className="py-6 sm:py-7">
          {memo.content.trim() ? (
            <Markdown content={memo.content} />
          ) : (
            <p className="text-gray-400 dark:text-gray-500">
              {t("records.blank")}
            </p>
          )}
        </div>
      </article>

      <section className="mt-6 border-gray-200/80 border-t pt-5 dark:border-gray-800">
        <div className="flex items-center justify-between gap-3">
          <h2 className="font-semibold text-gray-900 dark:text-gray-50">
            {t("entry.summary")}
          </h2>
          <button
            type="button"
            onClick={summarize}
            disabled={Boolean(busy) || !detailReady}
            className={subtleButtonClass}
          >
            <Sparkles className="h-4 w-4" />
            {busy === "summarize"
              ? t("entry.summarizing")
              : summary
                ? t("entry.resummarize")
                : t("entry.generateSummary")}
          </button>
        </div>
        {summary ? (
          <div className="mt-3 rounded-lg bg-gray-100/70 p-4 dark:bg-gray-900/70">
            <Markdown
              content={summary.summary || t("entry.emptySummary")}
              variant="chat"
            />
            <p className={`mt-3 text-xs ${mutedTextClass}`}>
              {t(
                summarySourceCount(summary) === 1
                  ? "entry.summarySourceOne"
                  : "entry.summarySourceMany",
                { count: summarySourceCount(summary) },
              )}
            </p>
          </div>
        ) : (
          <p className="mt-2 text-gray-400 text-sm dark:text-gray-500">
            {t("entry.summaryHint")}
          </p>
        )}
      </section>

      <section className="mt-5 border-gray-200/80 border-t pt-4 text-gray-500 text-sm dark:border-gray-800 dark:text-gray-400">
        <p>
          {t("entry.createdAt", {
            date: formatLocalDateTime(new Date(memo.createdAt), "full", locale),
          })}
        </p>
        {memo.version > 1 ? (
          <p className="mt-1">
            {t(
              memo.version - 1 === 1
                ? "entry.modifiedSummaryOne"
                : "entry.modifiedSummaryMany",
              {
                date: formatLocalDateTime(
                  new Date(memo.updatedAt),
                  "full",
                  locale,
                ),
                count: memo.version - 1,
              },
            )}
          </p>
        ) : null}
      </section>

      {error && !toast.available && !confirmingDelete ? (
        <p role="alert" className="mt-4 text-red-600 text-sm dark:text-red-400">
          {error}
        </p>
      ) : null}

      {confirmingDelete ? (
        <DeleteDialog
          busy={busy === "delete"}
          error={error}
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
  error,
  onCancel,
  onConfirm,
}: {
  busy: boolean;
  error: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const { t } = useI18n();
  const cancelRef = useRef<HTMLButtonElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const busyRef = useRef(busy);
  const onCancelRef = useRef(onCancel);
  const wasBusyRef = useRef(false);
  busyRef.current = busy;
  onCancelRef.current = onCancel;

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    cancelRef.current?.focus();
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !busyRef.current) {
        onCancelRef.current();
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, []);

  useEffect(() => {
    if (busy) {
      dialogRef.current?.focus();
    } else if (wasBusyRef.current) {
      confirmRef.current?.focus();
    }
    wasBusyRef.current = busy;
  }, [busy]);

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
    if (!first || !last) {
      event.preventDefault();
      dialogRef.current.focus();
      return;
    }
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center px-4">
      <button
        type="button"
        className="absolute inset-0 h-full w-full bg-gray-950/35 dark:bg-gray-950/65"
        aria-label={t("entry.cancelDelete")}
        tabIndex={-1}
        onClick={onCancel}
        disabled={busy}
      />
      <div
        ref={dialogRef}
        role="alertdialog"
        aria-modal="true"
        aria-busy={busy}
        aria-labelledby="delete-title"
        aria-describedby={
          error ? "delete-description delete-error" : "delete-description"
        }
        tabIndex={-1}
        onKeyDown={trapFocus}
        className="surface-enter relative w-full max-w-sm rounded-xl border border-gray-200 bg-white p-5 shadow-xl shadow-gray-950/15 dark:border-gray-700 dark:bg-gray-900 dark:shadow-black/30"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2
              id="delete-title"
              className="font-semibold text-gray-900 dark:text-gray-50"
            >
              {t("entry.deleteTitle")}
            </h2>
            <p
              id="delete-description"
              className="mt-1 text-gray-500 text-sm leading-6 dark:text-gray-400"
            >
              {t("entry.deleteDescription")}
            </p>
            {error ? (
              <p
                id="delete-error"
                role="alert"
                className="mt-2 text-red-600 text-sm dark:text-red-400"
              >
                {error}
              </p>
            ) : null}
          </div>
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            className={iconButtonClass}
            aria-label={t("common.close")}
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
            {t("common.cancel")}
          </button>
          <button
            ref={confirmRef}
            type="button"
            onClick={onConfirm}
            disabled={busy}
            className={`${dangerButtonClass} h-10 bg-red-600 px-4 text-white hover:bg-red-700 dark:bg-red-600 dark:text-white dark:hover:bg-red-500`}
          >
            {busy ? <LoaderCircle className="h-4 w-4 animate-spin" /> : null}
            {t(busy ? "common.deleting" : "common.confirmDelete")}
          </button>
        </div>
      </div>
    </div>
  );
}
