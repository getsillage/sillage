import type { KeyboardEvent, MouseEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import type { Memo } from "../lib/api";
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
}: {
  memo: Memo;
  openOnCardClick?: boolean;
}) {
  const navigate = useNavigate();
  const createdDate = memo.createdAt.slice(0, 10);
  const showEntryDate = memo.entryDate !== createdDate;
  const detailPath = `/entries/${memo.id}`;
  const preview = excerpt(memo.content) || "空白记录";

  function openDetail() {
    navigate(detailPath);
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
      <div className="flex flex-wrap items-center gap-2 text-gray-500 text-xs dark:text-gray-500">
        <LocalDateTime value={memo.createdAt} />
        {showEntryDate ? <time>归属 {memo.entryDate}</time> : null}
        {memo.pinnedAt ? <span>· 置顶</span> : null}
        {memo.version > 1 ? (
          <span>
            · 改于 <LocalDateTime value={memo.updatedAt} />
          </span>
        ) : null}
      </div>
      <h2 className="mt-1 text-[15px] text-gray-900 leading-7 dark:text-gray-100">
        {openOnCardClick ? (
          <span className="group-hover:text-gray-950 dark:group-hover:text-white">
            {preview}
          </span>
        ) : (
          <Link to={detailPath} className="hover:underline">
            {preview}
          </Link>
        )}
      </h2>
    </article>
  );
}
