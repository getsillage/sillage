import { Link, useFetcher } from "react-router";
import type { SummaryActionData } from "~/lib/product/summary-actions";

export interface InsightEntry {
  id: string;
  summary: string | null;
}

/** A per-entry AI insight with a "重新生成" affordance and a link back to its source. */
export function RecentInsightItem({ entry }: { entry: InsightEntry }) {
  const fetcher = useFetcher<SummaryActionData>();
  const busy = fetcher.state !== "idle";
  return (
    <li className="rounded-lg border border-gray-200 bg-white px-3 py-2 dark:border-gray-800 dark:bg-gray-950">
      <p className="text-gray-700 text-sm dark:text-gray-300">{entry.summary}</p>
      <div className="mt-2 flex items-center justify-between text-xs">
        <fetcher.Form method="post">
          <input type="hidden" name="intent" value="regenerate-entry" />
          <input type="hidden" name="entryId" value={entry.id} />
          <button
            type="submit"
            disabled={busy}
            className="text-gray-400 hover:text-gray-900 disabled:opacity-60 dark:hover:text-gray-100"
          >
            {busy ? "处理中…" : "重新生成"}
          </button>
        </fetcher.Form>
        <Link
          to={`/entries/${entry.id}`}
          className="text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
        >
          查看来源
        </Link>
      </div>
      {fetcher.data && !fetcher.data.ok ? (
        <p className="mt-2 text-red-600 text-xs dark:text-red-400">{fetcher.data.message}</p>
      ) : null}
    </li>
  );
}
