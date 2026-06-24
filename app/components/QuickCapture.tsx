import { useEffect, useRef, useState } from "react";
import { Link, useFetcher } from "react-router";
import { primaryButtonClass, textareaClass } from "./ui";

interface CaptureResult {
  ok: boolean;
  id?: string;
  error?: string;
}

const QUICK_MOODS: ReadonlyArray<{ value: number; label: string }> = [
  { value: 1, label: "低落" },
  { value: 2, label: "失落" },
  { value: 3, label: "平静" },
  { value: 4, label: "轻松" },
  { value: 5, label: "明亮" },
];

function moodChipClass(active: boolean): string {
  return active
    ? "rounded-full border border-gray-900 bg-gray-900 px-2.5 py-1 text-white text-xs dark:border-gray-100 dark:bg-gray-100 dark:text-gray-950"
    : "rounded-full border border-gray-200 px-2.5 py-1 text-gray-600 text-xs transition hover:border-gray-300 dark:border-gray-700 dark:text-gray-300 dark:hover:border-gray-600";
}

/**
 * Global quick-capture: a floating button (and ⌘/Ctrl+J) that opens a compact
 * composer reachable from any page. Posts a fragment to the action-only `/capture`
 * route via a fetcher, so it never navigates away; "写得更完整" jumps to 此刻 for the
 * full editor.
 */
export function QuickCapture() {
  const fetcher = useFetcher<CaptureResult>();
  const [open, setOpen] = useState(false);
  const [mood, setMood] = useState<number | null>(null);
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
      setMood(null);
    }
  }, [fetcher.state, fetcher.data]);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label="速记"
        title="速记（⌘/Ctrl + J）"
        className="fixed right-5 bottom-5 z-40 flex h-12 w-12 items-center justify-center rounded-full bg-gray-900 text-2xl text-white shadow-lg transition hover:bg-gray-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-900/30 dark:bg-gray-100 dark:text-gray-950 dark:hover:bg-white"
      >
        <span className={open ? "rotate-45 transition" : "transition"}>+</span>
      </button>

      {open ? (
        <div className="fixed inset-0 z-40">
          <button
            type="button"
            aria-label="关闭速记"
            onClick={() => setOpen(false)}
            className="absolute inset-0 h-full w-full cursor-default bg-gray-950/5 dark:bg-gray-950/30"
          />
          <div className="absolute right-5 bottom-20 w-[min(92vw,26rem)] rounded-xl border border-gray-200 bg-white p-4 shadow-xl dark:border-gray-800 dark:bg-gray-900">
            <fetcher.Form method="post" action="/capture" className="space-y-3">
              <input type="hidden" name="kind" value="fragment" />
              <input type="hidden" name="mood" value={mood ?? ""} />
              <textarea
                ref={textareaRef}
                name="body"
                rows={4}
                required
                placeholder="此刻留下些什么？"
                className={textareaClass}
              />
              <div className="flex flex-wrap gap-1.5">
                {QUICK_MOODS.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() =>
                      setMood((current) => (current === option.value ? null : option.value))
                    }
                    className={moodChipClass(mood === option.value)}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
              {fetcher.data && !fetcher.data.ok ? (
                <p className="text-red-600 text-xs dark:text-red-400">{fetcher.data.error}</p>
              ) : null}
              <div className="flex items-center justify-between">
                <Link
                  to="/"
                  onClick={() => setOpen(false)}
                  className="text-gray-500 text-xs hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
                >
                  写得更完整 →
                </Link>
                <button type="submit" disabled={busy} className={primaryButtonClass}>
                  {busy ? "保存中…" : "留下"}
                </button>
              </div>
            </fetcher.Form>
          </div>
        </div>
      ) : null}
    </>
  );
}
