import { Link, useFetcher } from "react-router";
import { LocalDateTime } from "~/components/LocalDateTime";
import { Markdown } from "~/components/Markdown";
import { subtleButtonClass } from "~/components/ui";
import type { SummaryActionData } from "~/lib/product/summary-actions";
import {
  isSummaryPeriodType,
  isSummaryStyle,
  PERIOD_TYPE_LABELS,
  STYLE_LABELS,
} from "~/lib/product/summary-fields";
import { badgeClass, statusClass } from "./shared";

export interface LoadedSummary {
  id: string;
  scope: string;
  periodType: string | null;
  startDate: string;
  endDate: string;
  style: string;
  title: string;
  content: string;
  sourceEntryIds: string[];
  generatedAt: Date;
}

/** One generated review: collapsed header with badges, expands to content + sources. */
export function SummaryCard({ summary }: { summary: LoadedSummary }) {
  const fetcher = useFetcher<SummaryActionData>();
  const busy = fetcher.state !== "idle";
  const scopeLabel =
    summary.scope === "topic"
      ? "主题"
      : summary.periodType && isSummaryPeriodType(summary.periodType)
        ? PERIOD_TYPE_LABELS[summary.periodType]
        : "时间范围";
  const styleLabel = isSummaryStyle(summary.style) ? STYLE_LABELS[summary.style] : summary.style;
  const range =
    summary.startDate === summary.endDate
      ? summary.startDate
      : `${summary.startDate} – ${summary.endDate}`;

  return (
    <details
      id={`summary-${summary.id}`}
      className="rounded-lg border border-gray-200 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-950"
    >
      <summary className="cursor-pointer list-none">
        <span className="font-medium text-gray-950 text-sm dark:text-gray-50">
          {summary.title || "未命名总结"}
        </span>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <span className={badgeClass()}>{scopeLabel}</span>
          <span className={badgeClass()}>{styleLabel}</span>
          <span className="text-gray-400 text-xs dark:text-gray-500">{range}</span>
          <span className="text-gray-400 text-xs dark:text-gray-500">
            · 基于 {summary.sourceEntryIds.length} 条 · 生成于{" "}
            <LocalDateTime value={summary.generatedAt} />
          </span>
        </div>
      </summary>

      <div className="mt-3 border-gray-100 border-t pt-3 dark:border-gray-800">
        <Markdown content={summary.content} />

        {summary.sourceEntryIds.length > 0 ? (
          <div className="mt-3 flex flex-wrap gap-2">
            {summary.sourceEntryIds.slice(0, 12).map((id, index) => (
              <Link
                key={id}
                to={`/entries/${id}`}
                className="text-gray-500 text-xs hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
              >
                来源 {index + 1}
              </Link>
            ))}
          </div>
        ) : null}

        <div className="mt-3 flex items-center gap-2">
          <fetcher.Form method="post">
            <input type="hidden" name="intent" value="regenerate-summary" />
            <input type="hidden" name="id" value={summary.id} />
            <button type="submit" disabled={busy} className={subtleButtonClass}>
              {busy ? "处理中…" : "重新生成"}
            </button>
          </fetcher.Form>
          <fetcher.Form
            method="post"
            onSubmit={(event) => {
              if (!confirm("确定删除这篇总结吗？")) {
                event.preventDefault();
              }
            }}
          >
            <input type="hidden" name="intent" value="delete" />
            <input type="hidden" name="id" value={summary.id} />
            <button type="submit" disabled={busy} className={subtleButtonClass}>
              删除
            </button>
          </fetcher.Form>
        </div>

        {fetcher.data && !fetcher.data.ok ? (
          <p className={`mt-3 ${statusClass(false)}`}>{fetcher.data.message}</p>
        ) : null}
      </div>
    </details>
  );
}
