import { ArrowRight, Plus, Save, X } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useToast } from "../../components/Toast";
import { useUnsavedChangesRegistration } from "../../components/UnsavedNavigationGuard";
import {
  ghostLinkClass,
  primaryButtonClass,
  textareaClass,
} from "../../components/ui";
import { useI18n } from "../../i18n/I18nProvider";
import { hasVisibleModal } from "../../lib/modal";

interface QuickCaptureProps {
  onCapture: (body: string) => Promise<void>;
  visible?: boolean;
}

const QUICK_CAPTURE_DRAFT_KEY = "sillage.quick-capture-draft";

function readDraft(): string {
  try {
    return window.localStorage.getItem(QUICK_CAPTURE_DRAFT_KEY) ?? "";
  } catch {
    return "";
  }
}

function writeDraft(body: string): void {
  try {
    if (body) {
      window.localStorage.setItem(QUICK_CAPTURE_DRAFT_KEY, body);
    } else {
      window.localStorage.removeItem(QUICK_CAPTURE_DRAFT_KEY);
    }
  } catch {
    // beforeunload and the global unsaved registration remain as fallbacks.
  }
}

/**
 * Global quick-capture: a floating button (and ⌘/Ctrl+J) that opens a compact
 * composer reachable from any page, so a stray thought never interrupts the
 * current view. "写得更完整" jumps to 记录 for the full editor.
 */
export function QuickCapture({ onCapture, visible = true }: QuickCaptureProps) {
  const { locale, t } = useI18n();
  const toast = useToast();
  const textareaId = useId();
  const [open, setOpen] = useState(false);
  const [body, setBody] = useState(readDraft);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const wasOpenRef = useRef(false);
  const submittingRef = useRef(false);
  const hasDraft = body.length > 0;
  const dialogOpen = visible && open;

  useUnsavedChangesRegistration(hasDraft);

  useEffect(() => {
    void locale;
    setError((current) => (current ? t("composer.saveFailed") : current));
  }, [locale, t]);

  useEffect(() => {
    writeDraft(body);
  }, [body]);

  useEffect(() => {
    if (!hasDraft) {
      return;
    }
    function warnBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault();
      event.returnValue = "";
    }
    window.addEventListener("beforeunload", warnBeforeUnload);
    return () => window.removeEventListener("beforeunload", warnBeforeUnload);
  }, [hasDraft]);

  useEffect(() => {
    if (!visible) {
      setOpen(false);
    }
  }, [visible]);

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "j") {
        event.preventDefault();
        if (
          !visible ||
          submittingRef.current ||
          hasVisibleModal(dialogRef.current)
        ) {
          return;
        }
        setOpen((value) => !value);
      } else if (event.key === "Escape") {
        if (submittingRef.current || hasVisibleModal(dialogRef.current)) {
          return;
        }
        setOpen(false);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [visible]);

  useEffect(() => {
    if (dialogOpen) {
      wasOpenRef.current = true;
      const previousOverflow = document.body.style.overflow;
      document.body.style.overflow = "hidden";
      textareaRef.current?.focus();
      return () => {
        document.body.style.overflow = previousOverflow;
      };
    }
    if (wasOpenRef.current) {
      triggerRef.current?.focus();
      wasOpenRef.current = false;
    }
  }, [dialogOpen]);

  // Keep Tab focus inside the open dialog (simple two-end wrap trap).
  function onDialogKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    if (event.key !== "Tab" || !dialogRef.current) {
      return;
    }
    const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), textarea, input, [tabindex]:not([tabindex="-1"])',
    );
    if (focusable.length === 0) {
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  async function submit() {
    if (submittingRef.current) {
      return;
    }
    const trimmed = body.trim();
    if (!trimmed) {
      const message = t("quick.prompt");
      setError(message);
      toast.showToast({ kind: "info", message });
      return;
    }
    submittingRef.current = true;
    setBusy(true);
    setError("");
    try {
      await onCapture(trimmed);
      writeDraft("");
      setBody("");
      setOpen(false);
      toast.showToast({ kind: "success", message: t("quick.saved") });
    } catch (cause) {
      const message =
        cause instanceof Error ? cause.message : t("composer.saveFailed");
      setError(message);
      toast.showToast({ kind: "error", message });
    } finally {
      submittingRef.current = false;
      setBusy(false);
    }
  }

  return (
    <>
      {visible ? (
        <button
          ref={triggerRef}
          type="button"
          onClick={() => {
            if (!submittingRef.current) {
              setOpen((value) => !value);
            }
          }}
          disabled={busy}
          aria-label={t("quick.title")}
          title={t("quick.shortcutTitle")}
          className="fixed right-4 bottom-[max(1rem,env(safe-area-inset-bottom))] z-40 flex h-12 w-12 items-center justify-center rounded-full bg-gray-900 text-white shadow-xl shadow-gray-900/15 transition-colors hover:bg-gray-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/40 disabled:cursor-not-allowed disabled:opacity-60 sm:right-5 dark:bg-gray-100 dark:text-gray-900 dark:hover:bg-white dark:focus-visible:ring-gray-500/40"
        >
          <Plus
            className={
              open ? "h-5 w-5 rotate-45 transition" : "h-5 w-5 transition"
            }
          />
        </button>
      ) : null}

      {dialogOpen ? (
        <div className="fixed inset-0 z-40">
          <button
            type="button"
            aria-label={t("quick.close")}
            onClick={() => {
              if (!submittingRef.current) {
                setOpen(false);
              }
            }}
            className="absolute inset-0 h-full w-full cursor-default bg-gray-950/10 dark:bg-gray-950/50"
          />
          <div
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-label={t("quick.title")}
            onKeyDown={onDialogKeyDown}
            className="surface-enter absolute right-3 bottom-[max(5rem,calc(env(safe-area-inset-bottom)+4rem))] left-3 rounded-xl border border-gray-200/80 bg-white/95 p-3 shadow-xl shadow-gray-900/10 backdrop-blur-xl sm:right-5 sm:left-auto sm:w-[min(92vw,26rem)] dark:border-gray-800 dark:bg-gray-900/95 dark:shadow-black/30"
          >
            <div className="space-y-3">
              <div className="flex items-center justify-between gap-3 px-1">
                <h2 className="font-medium text-gray-900 text-sm dark:text-gray-50">
                  {t("quick.title")}
                </h2>
                <button
                  type="button"
                  onClick={() => setOpen(false)}
                  disabled={busy}
                  className="inline-flex h-10 w-10 items-center justify-center rounded-lg text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-50"
                  aria-label={t("quick.close")}
                  title={t("common.close")}
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
              <label className="sr-only" htmlFor={textareaId}>
                {t("quick.contentLabel")}
              </label>
              <textarea
                id={textareaId}
                ref={textareaRef}
                value={body}
                onChange={(event) => setBody(event.target.value)}
                disabled={busy}
                onKeyDown={(event) => {
                  if (
                    (event.metaKey || event.ctrlKey) &&
                    event.key === "Enter"
                  ) {
                    event.preventDefault();
                    void submit();
                  }
                }}
                rows={4}
                placeholder={t("quick.prompt")}
                className={`${textareaClass} min-h-28 resize-none border-0 bg-gray-50 dark:bg-gray-950`}
              />
              {error && !toast.available ? (
                <p
                  role="alert"
                  className="text-red-600 text-xs dark:text-red-400"
                >
                  {error}
                </p>
              ) : null}
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <Link
                  to="/"
                  aria-disabled={busy}
                  onClick={(event) => {
                    if (busy) {
                      event.preventDefault();
                      return;
                    }
                    setOpen(false);
                  }}
                  className={`${ghostLinkClass} text-xs`}
                >
                  {t("quick.writeMore")}
                  <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                </Link>
                <button
                  type="button"
                  onClick={submit}
                  disabled={busy}
                  className={`${primaryButtonClass} w-full sm:w-auto`}
                >
                  <Save className="h-4 w-4" aria-hidden="true" />
                  {t(busy ? "common.saving" : "common.save")}
                </button>
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
