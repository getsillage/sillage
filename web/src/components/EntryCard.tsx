import { ChevronRight, Pin } from "lucide-react";
import type { KeyboardEvent, MouseEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import type { Memo } from "../lib/api";
import { formatShortDate, toLocalISODate } from "../lib/date";
import { excerpt } from "../lib/memos";
import { LocalDateTime } from "./LocalDateTime";
import { rowLinkClass } from "./ui";

function isInteractiveTarget(target: EventTarget | null): boolean {
  return (
    target instanceof Element &&
    target.closest(
      "a, button, input, textarea, select, summary, [role='button']",
    ) !== null
  );
}

/** One record as a compact list row; whole-row click opens its detail page. */
export function EntryCard({
  memo,
  openOnCardClick = false,
  grouped = false,
}: {
  memo: Memo;
  openOnCardClick?: boolean;
  grouped?: boolean;
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const createdAt = new Date(memo.createdAt);
  const createdDate = Number.isNaN(createdAt.getTime())
    ? memo.createdAt.slice(0, 10)
    : toLocalISODate(createdAt);
  const showEntryDate = memo.entryDate !== createdDate;
  const detailPath = `/entries/${memo.id}`;
  const returnTo = `${location.pathname}${location.search}${location.hash}`;
  const preview = excerpt(memo.content) || "空白记录";

  function openDetail() {
    navigate(detailPath, { state: { returnTo } });
  }

  function handleCardClick(event: MouseEvent<HTMLElement>) {
    if (event.defaultPrevented || isInteractiveTarget(event.target)) {
      return;
    }
    openDetail();
  }

  function handleCardKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.defaultPrevented || event.currentTarget !== event.target) {
      return;
    }
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      openDetail();
    }
  }

  return (
    <article
      className={`${rowLinkClass}${openOnCardClick ? " group cursor-pointer" : ""}`}
      role={openOnCardClick ? "link" : undefined}
      tabIndex={openOnCardClick ? 0 : undefined}
      aria-label={openOnCardClick ? `查看${preview}详情` : undefined}
      onClick={openOnCardClick ? handleCardClick : undefined}
      onKeyDown={openOnCardClick ? handleCardKeyDown : undefined}
    >
      <div className="flex items-center gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-gray-500 text-xs dark:text-gray-400">
            {grouped ? (
              showEntryDate ? (
                <span>
                  记录于{" "}
                  <LocalDateTime value={memo.createdAt} variant="short" />
                </span>
              ) : (
                <LocalDateTime value={memo.createdAt} variant="time" />
              )
            ) : (
              <LocalDateTime value={memo.createdAt} />
            )}
            {!grouped && showEntryDate ? (
              <time dateTime={memo.entryDate}>
                归属 {formatShortDate(memo.entryDate)}
              </time>
            ) : null}
            {memo.pinnedAt ? (
              <span className="inline-flex items-center gap-1 text-gray-600 dark:text-gray-300">
                <Pin className="h-3 w-3" aria-hidden="true" />
                置顶
              </span>
            ) : null}
            {memo.version > 1 ? (
              <span>
                改于 <LocalDateTime value={memo.updatedAt} variant="short" />
              </span>
            ) : null}
          </div>
          <p className="mt-1 text-[15px] text-gray-900 leading-7 dark:text-gray-100">
            {openOnCardClick ? (
              <span className="group-hover:text-gray-950 dark:group-hover:text-white">
                {preview}
              </span>
            ) : (
              <Link
                to={detailPath}
                state={{ returnTo }}
                className="hover:underline"
              >
                {preview}
              </Link>
            )}
          </p>
        </div>
        {openOnCardClick ? (
          <ChevronRight
            aria-hidden="true"
            className="h-4 w-4 flex-none text-gray-300 transition-transform group-hover:translate-x-0.5 group-hover:text-gray-500 dark:text-gray-700 dark:group-hover:text-gray-400"
          />
        ) : null}
      </div>
    </article>
  );
}
