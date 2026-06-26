import type { KeyboardEvent, MouseEvent } from "react";
import { Link, useNavigate } from "react-router";
import type { EntryWithTags } from "~/lib/db/entries";
import { LocalDateTime } from "./LocalDateTime";
import { rowLinkClass, serifTitleClass } from "./ui";

function excerpt(body: string, max = 120): string {
  const text = body.replace(/\s+/g, " ").trim();
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

function isInteractiveTarget(target: EventTarget | null): boolean {
  return (
    target instanceof Element &&
    target.closest("a, button, input, textarea, select, summary, [role='button']") !== null
  );
}

export function EntryCard({
  entry,
  openOnCardClick = false,
}: {
  entry: EntryWithTags;
  showEntryInsight?: boolean;
  openOnCardClick?: boolean;
}) {
  const navigate = useNavigate();
  const createdDate = entry.createdAt.toISOString().slice(0, 10);
  const showEntryDate = entry.entryDate !== createdDate;
  const detailPath = `/entries/${entry.id}`;
  const title = excerpt(entry.body, 48) || "空白记录";

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
      aria-label={openOnCardClick ? `查看${title}详情` : undefined}
      onClick={openOnCardClick ? handleCardClick : undefined}
      onKeyDown={openOnCardClick ? handleCardKeyDown : undefined}
    >
      <div className="flex flex-wrap items-center gap-2 text-gray-400 text-xs dark:text-gray-500">
        <LocalDateTime value={entry.createdAt} />
        {showEntryDate ? <time>归属 {entry.entryDate}</time> : null}
        {entry.version > 1 ? (
          <span className="text-gray-400 dark:text-gray-500">
            · 改于 <LocalDateTime value={entry.updatedAt} />
          </span>
        ) : null}
      </div>
      <h2 className={`mt-1 text-base ${serifTitleClass}`}>
        {openOnCardClick ? (
          <span className="group-hover:text-celadon-700 dark:group-hover:text-celadon-200">
            {title}
          </span>
        ) : (
          <Link to={detailPath} className="hover:text-celadon-700 dark:hover:text-celadon-200">
            {title}
          </Link>
        )}
      </h2>
      {entry.body ? (
        <p className="mt-1 text-gray-500 text-sm leading-6 dark:text-gray-400">
          {excerpt(entry.body)}
        </p>
      ) : null}
    </article>
  );
}
