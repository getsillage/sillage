import { useEffect, useRef, useState } from "react";
import { Link, useFetcher } from "react-router";
import { primaryButtonClass, textareaClass } from "./ui";

interface CaptureResult {
  ok: boolean;
  id?: string;
  error?: string;
}

/**
 * Global quick-capture: a floating button (and ⌘/Ctrl+J) that opens a compact
 * composer reachable from any page. Posts a record to the action-only `/capture`
 * route via a fetcher, so it never navigates away; "写得更完整" jumps to 记录 for the
 * full editor.
 */
export function QuickCapture() {
  const fetcher = useFetcher<CaptureResult>();
  const [open, setOpen] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const busy = fetcher.state !== "idle";

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

  // Close and reset once a capture succeeds.
  useEffect(() => {
    if (fetcher.state === "idle" && fetcher.data?.ok) {
      setOpen(false);
    }
  }, [fetcher.state, fetcher.data]);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label="速记"
        title="速记（⌘/Ctrl + J）"
        className="fixed right-4 bottom-4 z-40 flex h-12 w-12 items-center justify-center rounded-full bg-celadon-600 text-2xl text-white shadow-lg shadow-gray-900/15 transition hover:bg-celadon-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/30 sm:right-5 sm:bottom-5 dark:bg-celadon-500 dark:text-gray-950 dark:hover:bg-celadon-400 dark:focus-visible:ring-celadon-400/30"
      >
        <span className={open ? "rotate-45 transition" : "transition"}>+</span>
      </button>

      {open ? (
        <div className="fixed inset-0 z-40">
          <button
            type="button"
            aria-label="关闭速记"
            onClick={() => setOpen(false)}
            className="absolute inset-0 h-full w-full cursor-default bg-gray-950/10 dark:bg-gray-950/50"
          />
          <div className="absolute right-3 bottom-20 left-3 rounded-xl border border-gray-200 bg-white p-4 shadow-xl shadow-gray-900/10 sm:right-5 sm:left-auto sm:w-[min(92vw,26rem)] dark:border-gray-800 dark:bg-gray-900 dark:shadow-black/30">
            <fetcher.Form method="post" action="/capture" className="space-y-3">
              <textarea
                ref={textareaRef}
                name="body"
                rows={4}
                required
                placeholder="想记录什么？"
                className={textareaClass}
              />
              {fetcher.data && !fetcher.data.ok ? (
                <p className="text-red-600 text-xs dark:text-red-400">{fetcher.data.error}</p>
              ) : null}
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <Link
                  to="/"
                  onClick={() => setOpen(false)}
                  className="text-gray-500 text-xs hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
                >
                  写得更完整 →
                </Link>
                <button
                  type="submit"
                  disabled={busy}
                  className={`${primaryButtonClass} w-full sm:w-auto`}
                >
                  {busy ? "保存中…" : "保存"}
                </button>
              </div>
            </fetcher.Form>
          </div>
        </div>
      ) : null}
    </>
  );
}
