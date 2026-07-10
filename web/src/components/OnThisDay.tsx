import { Link, useLocation } from "react-router-dom";
import type { Memo } from "../lib/api";
import { yearsBetween } from "../lib/date";
import { excerpt } from "../lib/memos";
import { mutedTextClass } from "./ui";

interface OnThisDayProps {
  entries: Memo[];
  today: string;
}

/** "那年今日" — memos written on this day in earlier years. Renders nothing
 *  when there are none, so callers can drop it in unconditionally. */
export function OnThisDay({ entries, today }: OnThisDayProps) {
  const location = useLocation();
  const returnTo = `${location.pathname}${location.search}${location.hash}`;
  if (entries.length === 0) {
    return null;
  }
  return (
    <section className="rounded-lg border border-gray-200/70 bg-white/65 py-4 pr-16 pl-4 shadow-sm shadow-gray-900/[0.02] sm:pr-4 dark:border-gray-800 dark:bg-gray-900/50">
      <h2 className="font-medium text-gray-500 text-xs dark:text-gray-400">
        那年今日
      </h2>
      <ul className="mt-2 space-y-1">
        {entries.map((memo) => (
          <li key={memo.id}>
            <Link
              to={`/entries/${memo.id}`}
              state={{ returnTo }}
              className="block rounded-md px-2 py-1.5 text-gray-700 text-sm transition hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:text-gray-200 dark:hover:bg-gray-800 dark:focus-visible:ring-gray-500/40"
            >
              <span className={mutedTextClass}>
                {yearsBetween(memo.entryDate, today)}年前
              </span>
              <span className="mx-1.5 text-gray-400">·</span>
              {excerpt(memo.content, 56) || "空白记录"}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
