import { Check, Save } from "lucide-react";
import type { KeyboardEvent as ReactKeyboardEvent, RefObject } from "react";
import { useEffect, useRef, useState } from "react";
import { UnsavedNavigationGuard } from "../../components/UnsavedNavigationGuard";
import {
  dangerButtonClass,
  inputClass,
  primaryButtonClass,
  secondaryButtonClass,
  subtleButtonClass,
} from "../../components/ui";
import { useI18n } from "../../i18n/I18nProvider";
import { todayISO } from "../../lib/date";
import { MarkdownEditor } from "./MarkdownEditor";
import type { UploadedAttachment } from "./MemosContext";

interface EntryComposerProps {
  draftKey: string;
  mode?: "create" | "edit";
  initialContent?: string;
  initialEntryDate?: string;
  initialVersion?: number;
  submitLabel?: string;
  onSubmit: (input: { content: string; entryDate: string }) => Promise<void>;
  onUpload: (file: File) => Promise<UploadedAttachment>;
  onCancel?: () => void;
}

interface StoredEntryDraft {
  version: 2;
  content: string;
  entryDate: string;
  baseVersion: number | null;
}

interface StoredDraftState {
  draft: StoredEntryDraft | null;
  conflict: StoredEntryDraft | null;
}

const DRAFT_STORAGE_PREFIX = "sillage.entry-draft.";

function storageKeyFor(draftKey: string): string {
  return `${DRAFT_STORAGE_PREFIX}${draftKey}`;
}

function removeStoredDraft(storageKey: string) {
  try {
    window.localStorage.removeItem(storageKey);
  } catch {
    // beforeunload still protects the in-memory draft when storage is unavailable.
  }
}

function readStoredDraft(
  storageKey: string,
  initialContent: string,
  initialEntryDate: string,
  mode: "create" | "edit",
  initialVersion?: number,
): StoredDraftState {
  try {
    const raw = window.localStorage.getItem(storageKey);
    if (!raw) {
      return { draft: null, conflict: null };
    }
    const parsed = JSON.parse(raw) as {
      version?: unknown;
      content?: unknown;
      entryDate?: unknown;
      baseVersion?: unknown;
    };
    if (
      (parsed.version !== 1 && parsed.version !== 2) ||
      typeof parsed.content !== "string" ||
      typeof parsed.entryDate !== "string"
    ) {
      removeStoredDraft(storageKey);
      return { draft: null, conflict: null };
    }
    if (
      parsed.content === initialContent &&
      parsed.entryDate === initialEntryDate
    ) {
      removeStoredDraft(storageKey);
      return { draft: null, conflict: null };
    }
    const draft: StoredEntryDraft = {
      version: 2,
      content: parsed.content,
      entryDate: parsed.entryDate,
      baseVersion:
        parsed.version === 2 &&
        (typeof parsed.baseVersion === "number" || parsed.baseVersion === null)
          ? parsed.baseVersion
          : null,
    };
    const currentVersion = initialVersion ?? null;
    const conflictsWithServer =
      mode === "edit" &&
      (parsed.version !== 2 || draft.baseVersion !== currentVersion);
    return conflictsWithServer
      ? { draft: null, conflict: draft }
      : { draft, conflict: null };
  } catch {
    removeStoredDraft(storageKey);
    return { draft: null, conflict: null };
  }
}

function writeStoredDraft(storageKey: string, draft: StoredEntryDraft) {
  try {
    window.localStorage.setItem(storageKey, JSON.stringify(draft));
  } catch {
    // The active beforeunload guard remains the fallback for storage failures.
  }
}

/** Unified record editor: an entry date, a Markdown body, and a save action. */
export function EntryComposer({
  draftKey,
  mode = "create",
  initialContent = "",
  initialEntryDate,
  initialVersion,
  submitLabel,
  onSubmit,
  onUpload,
  onCancel,
}: EntryComposerProps) {
  const { locale, t } = useI18n();
  const startingEntryDate = initialEntryDate ?? todayISO();
  const storageKey = storageKeyFor(draftKey);
  const baselineVersionRef = useRef(initialVersion ?? null);
  const [storedDraft] = useState(() =>
    readStoredDraft(
      storageKey,
      initialContent,
      startingEntryDate,
      mode,
      baselineVersionRef.current ?? undefined,
    ),
  );
  const restoredDraft = storedDraft.draft;
  const [conflictingDraft, setConflictingDraft] = useState(
    storedDraft.conflict,
  );
  const [content, setContent] = useState(
    restoredDraft?.content ?? initialContent,
  );
  const [entryDate, setEntryDate] = useState(
    restoredDraft?.entryDate ?? startingEntryDate,
  );
  const [baseline, setBaseline] = useState({
    content: initialContent,
    entryDate: startingEntryDate,
  });
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(
    restoredDraft ? t("composer.restoredDraft") : "",
  );
  const effectiveSubmitLabel = submitLabel ?? t("common.save");
  const dirty =
    content !== baseline.content || entryDate !== baseline.entryDate;
  const submittingRef = useRef(false);
  const cancelButtonRef = useRef<HTMLButtonElement>(null);
  const feedbackLocaleRef = useRef(locale);

  useEffect(() => {
    if (feedbackLocaleRef.current === locale) {
      return;
    }
    feedbackLocaleRef.current = locale;
    setError("");
    setNotice("");
  }, [locale]);

  useEffect(() => {
    if (conflictingDraft) {
      return;
    }
    if (dirty) {
      writeStoredDraft(storageKey, {
        version: 2,
        content,
        entryDate,
        baseVersion: baselineVersionRef.current,
      });
    } else {
      removeStoredDraft(storageKey);
    }
  }, [conflictingDraft, content, dirty, entryDate, storageKey]);

  useEffect(() => {
    if (!dirty && !uploading && !conflictingDraft) {
      return;
    }
    function warnBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault();
      event.returnValue = "";
    }
    window.addEventListener("beforeunload", warnBeforeUnload);
    return () => window.removeEventListener("beforeunload", warnBeforeUnload);
  }, [conflictingDraft, dirty, uploading]);

  async function handleSubmit() {
    if (submittingRef.current || busy || uploading || conflictingDraft) {
      return;
    }
    if (!content.trim()) {
      setError(t("composer.submitRequired"));
      return;
    }
    submittingRef.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await onSubmit({ content, entryDate });
      removeStoredDraft(storageKey);
      if (mode === "create") {
        const resetEntryDate = todayISO();
        setContent("");
        setEntryDate(resetEntryDate);
        setBaseline({ content: "", entryDate: resetEntryDate });
      } else {
        setBaseline({ content, entryDate });
      }
      setNotice(t("composer.saved"));
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : t("composer.saveFailed"),
      );
    } finally {
      submittingRef.current = false;
      setBusy(false);
    }
  }

  function handleCancel() {
    if (!onCancel || busy || uploading) {
      return;
    }
    if (dirty || conflictingDraft) {
      setConfirmingCancel(true);
      return;
    }
    onCancel();
  }

  function discardAndCancel() {
    removeStoredDraft(storageKey);
    setBaseline({ content, entryDate });
    setConflictingDraft(null);
    setConfirmingCancel(false);
    onCancel?.();
  }

  return (
    <div className="space-y-3">
      <UnsavedNavigationGuard
        when={dirty || uploading || Boolean(conflictingDraft)}
        title={t(
          uploading ? "composer.uploadUnsavedTitle" : "composer.unsavedTitle",
        )}
        description={
          uploading
            ? t("composer.uploadUnsavedDescription")
            : t("composer.unsavedDescription")
        }
      />
      {confirmingCancel ? (
        <DiscardDraftDialog
          returnFocusRef={cancelButtonRef}
          onCancel={() => setConfirmingCancel(false)}
          onConfirm={discardAndCancel}
        />
      ) : null}
      <label className="block text-sm text-gray-500 dark:text-gray-400">
        <span className="font-medium text-gray-700 dark:text-gray-300">
          {t("composer.date")}
        </span>
        <input
          type="date"
          value={entryDate}
          disabled={busy}
          onChange={(event) => {
            setEntryDate(event.target.value);
            setNotice("");
          }}
          className={`${inputClass} max-w-44`}
        />
      </label>

      <MarkdownEditor
        value={content}
        disabled={busy}
        onChange={(value) => {
          setContent(value);
          setNotice("");
        }}
        onUpload={onUpload}
        onUploadingChange={setUploading}
      />

      {conflictingDraft ? (
        <div
          role="alert"
          className="rounded-lg border border-gray-300 bg-gray-100/70 p-3 dark:border-gray-700 dark:bg-gray-900"
        >
          <p className="font-medium text-gray-900 text-sm dark:text-gray-50">
            {t("composer.conflictTitle")}
          </p>
          <p className="mt-1 text-gray-500 text-sm dark:text-gray-400">
            {t("composer.conflictDescription")}
          </p>
          <div className="mt-3 flex flex-wrap justify-end gap-2">
            <button
              type="button"
              className={subtleButtonClass}
              onClick={() => {
                removeStoredDraft(storageKey);
                setConflictingDraft(null);
                setNotice(t("composer.keptServer"));
              }}
            >
              {t("composer.keepServer")}
            </button>
            <button
              type="button"
              className={primaryButtonClass}
              onClick={() => {
                setContent(conflictingDraft.content);
                setEntryDate(conflictingDraft.entryDate);
                setConflictingDraft(null);
                setNotice(t("composer.restoredConflictDraft"));
              }}
            >
              {t("composer.restoreDraft")}
            </button>
          </div>
        </div>
      ) : null}

      {error ? (
        <p className="text-red-600 text-sm dark:text-red-400">{error}</p>
      ) : null}
      {notice ? (
        <p
          role="status"
          className="inline-flex items-center gap-1.5 text-gray-500 text-sm dark:text-gray-400"
        >
          <Check className="h-4 w-4" />
          {notice}
        </p>
      ) : null}

      <div className="flex justify-end gap-2">
        {onCancel ? (
          <button
            ref={cancelButtonRef}
            type="button"
            onClick={handleCancel}
            disabled={busy || uploading}
            className={subtleButtonClass}
          >
            {t("common.cancel")}
          </button>
        ) : null}
        <button
          type="button"
          onClick={handleSubmit}
          disabled={busy || uploading || Boolean(conflictingDraft)}
          className={`${primaryButtonClass} w-full sm:w-auto`}
        >
          <Save className="h-4 w-4" />
          {uploading
            ? t("composer.attachmentUploading")
            : busy
              ? t("common.saving")
              : effectiveSubmitLabel}
        </button>
      </div>
    </div>
  );
}

function DiscardDraftDialog({
  returnFocusRef,
  onCancel,
  onConfirm,
}: {
  returnFocusRef: RefObject<HTMLButtonElement | null>;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const { t } = useI18n();
  const dialogRef = useRef<HTMLDivElement>(null);
  const stayButtonRef = useRef<HTMLButtonElement>(null);
  const discardingRef = useRef(false);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    stayButtonRef.current?.focus();
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        onCancel();
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previousOverflow;
      if (!discardingRef.current) {
        returnFocusRef.current?.focus();
      }
    };
  }, [onCancel, returnFocusRef]);

  function trapFocus(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key !== "Tab" || !dialogRef.current) {
      return;
    }
    const focusable = dialogRef.current.querySelectorAll<HTMLButtonElement>(
      "button:not([disabled])",
    );
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (!first || !last) {
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
    <div className="fixed inset-0 z-[70] grid place-items-center px-4">
      <button
        type="button"
        aria-label={t("composer.stayEditor")}
        tabIndex={-1}
        className="absolute inset-0 h-full w-full bg-gray-950/35 dark:bg-gray-950/70"
        onClick={onCancel}
      />
      <div
        ref={dialogRef}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="discard-draft-title"
        aria-describedby="discard-draft-description"
        onKeyDown={trapFocus}
        className="surface-enter relative w-full max-w-sm rounded-xl border border-gray-200 bg-white p-5 shadow-xl shadow-gray-950/15 dark:border-gray-700 dark:bg-gray-900 dark:shadow-black/35"
      >
        <h2
          id="discard-draft-title"
          className="font-semibold text-gray-900 text-lg dark:text-gray-50"
        >
          {t("composer.discardTitle")}
        </h2>
        <p
          id="discard-draft-description"
          className="mt-2 text-gray-500 text-sm leading-6 dark:text-gray-400"
        >
          {t("composer.discardDescription")}
        </p>
        <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button
            ref={stayButtonRef}
            type="button"
            className={secondaryButtonClass}
            onClick={onCancel}
          >
            {t("unsaved.keepEditing")}
          </button>
          <button
            type="button"
            className={dangerButtonClass}
            onClick={() => {
              discardingRef.current = true;
              onConfirm();
            }}
          >
            {t("composer.discard")}
          </button>
        </div>
      </div>
    </div>
  );
}
