import { ChevronRight, Star } from "lucide-react";
import type { KeyboardEvent, MouseEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { rowLinkClass } from "../../components/ui";
import { useI18n } from "../../i18n/I18nProvider";
import type { Memo } from "../../lib/api";
import { formatShortDate, toLocalISODate } from "../../lib/date";
import { formatLocalDateTime, LocalDateTime } from "./LocalDateTime";
import { excerpt } from "./memos";

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
  const { locale, t } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const createdAt = new Date(memo.createdAt);
  const createdDate = Number.isNaN(createdAt.getTime())
    ? memo.createdAt.slice(0, 10)
    : toLocalISODate(createdAt);
  const showEntryDate = memo.entryDate !== createdDate;
  const detailPath = `/entries/${memo.id}`;
  const returnTo = `${location.pathname}${location.search}${location.hash}`;
  const detailState = { returnTo, memoSnapshot: { ...memo } };
  const preview = excerpt(memo.content) || t("records.blank");

  function openDetail() {
    navigate(detailPath, { state: detailState });
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
      aria-label={
        openOnCardClick ? t("records.viewDetails", { preview }) : undefined
      }
      onClick={openOnCardClick ? handleCardClick : undefined}
      onKeyDown={openOnCardClick ? handleCardKeyDown : undefined}
    >
      <div className="flex items-center gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-gray-500 text-xs dark:text-gray-400">
            {grouped ? (
              showEntryDate ? (
                <span>
                  {t("records.recordedAt", {
                    date: formatLocalDateTime(createdAt, "short", locale),
                  })}
                </span>
              ) : (
                <LocalDateTime value={memo.createdAt} variant="time" />
              )
            ) : (
              <LocalDateTime value={memo.createdAt} />
            )}
            {!grouped && showEntryDate ? (
              <time dateTime={memo.entryDate}>
                {t("records.belongsTo", {
                  date: formatShortDate(memo.entryDate, undefined, locale),
                })}
              </time>
            ) : null}
            {memo.favoritedAt ? (
              <span className="inline-flex items-center gap-1 text-gray-600 dark:text-gray-300">
                <Star className="h-3 w-3 fill-current" aria-hidden="true" />
                {t("records.favorite")}
              </span>
            ) : null}
            {memo.version > 1 ? (
              <span>
                {t("records.modifiedAt", {
                  date: formatLocalDateTime(
                    new Date(memo.updatedAt),
                    "short",
                    locale,
                  ),
                })}
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
                state={detailState}
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
