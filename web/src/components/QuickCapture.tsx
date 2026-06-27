import { Plus } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ghostLinkClass, primaryButtonClass, textareaClass } from "./ui";

interface QuickCaptureProps {
  onCapture: (body: string) => Promise<void>;
}

/**
 * Global quick-capture: a floating button (and ⌘/Ctrl+J) that opens a compact
 * composer reachable from any page, so a stray thought never interrupts the
 * current view. "写得更完整" jumps to 记录 for the full editor.
 */
export function QuickCapture({ onCapture }: QuickCaptureProps) {
  const [open, setOpen] = useState(false);
  const [body, setBody] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "j") {
        event.preventDefault();
        setOpen((value) => !value);
      } else if (event.key === "Escape") {
        setOpen(false);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  useEffect(() => {
    if (open) {
      textareaRef.current?.focus();
    }
  }, [open]);

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
    if (!body.trim()) {
      setError("想记录什么？");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await onCapture(body);
      setBody("");
      setOpen(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label="速记"
        title="速记（⌘/Ctrl + J）"
        className="fixed right-4 bottom-6 z-40 flex h-12 w-12 items-center justify-center rounded-full bg-gray-900 text-white shadow-xl shadow-gray-900/15 transition hover:bg-gray-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/40 sm:right-5 sm:bottom-5 dark:bg-gray-100 dark:text-gray-900 dark:hover:bg-white dark:focus-visible:ring-gray-500/40"
      >
        <Plus
          className={
            open ? "h-5 w-5 rotate-45 transition" : "h-5 w-5 transition"
          }
        />
      </button>

      {open ? (
        <div className="fixed inset-0 z-40">
          <button
            type="button"
            aria-label="关闭速记"
            onClick={() => setOpen(false)}
            className="absolute inset-0 h-full w-full cursor-default bg-gray-950/10 dark:bg-gray-950/50"
          />
          <div
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-label="速记"
            onKeyDown={onDialogKeyDown}
            className="absolute right-3 bottom-20 left-3 rounded-2xl border border-gray-200/80 bg-white/95 p-3 shadow-xl shadow-gray-900/10 backdrop-blur-xl sm:right-5 sm:left-auto sm:w-[min(92vw,26rem)] dark:border-gray-800 dark:bg-gray-900/95 dark:shadow-black/30"
          >
            <div className="space-y-3">
              <textarea
                ref={textareaRef}
                value={body}
                onChange={(event) => setBody(event.target.value)}
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
                placeholder="想记录什么？"
                className={`${textareaClass} min-h-28 resize-none border-0 bg-gray-50 focus:ring-0 dark:bg-gray-950`}
              />
              {error ? (
                <p className="text-red-600 text-xs dark:text-red-400">
                  {error}
                </p>
              ) : null}
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <Link
                  to="/"
                  onClick={() => setOpen(false)}
                  className={`${ghostLinkClass} text-xs`}
                >
                  写得更完整 →
                </Link>
                <button
                  type="button"
                  onClick={submit}
                  disabled={busy}
                  className={`${primaryButtonClass} w-full sm:w-auto`}
                >
                  {busy ? "保存中…" : "保存"}
                </button>
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
