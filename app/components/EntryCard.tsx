import type { KeyboardEvent, MouseEvent } from "react";
import { Link, useNavigate } from "react-router";
import { EntryInsightControl } from "~/components/ai/EntryInsightControl";
import type { EntryWithTags } from "~/lib/db/entries";
import {
  entryKindLabel,
  normalizeEntryKind,
  normalizeNoteType,
  noteTypeLabel,
  parseTextList,
} from "~/lib/product/entry-fields";
import { LocalDateTime } from "./LocalDateTime";
import { rowLinkClass } from "./ui";

const MOOD_LABEL: Record<number, string> = {
  1: "低落",
  2: "失落",
  3: "平静",
  4: "轻松",
  5: "明亮",
};

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
  showEntryInsight = false,
  openOnCardClick = false,
}: {
  entry: EntryWithTags;
  showEntryInsight?: boolean;
  openOnCardClick?: boolean;
}) {
  const navigate = useNavigate();
  const kind = normalizeEntryKind(entry.kind);
  const noteLabel = noteTypeLabel(normalizeNoteType(entry.noteType, kind));
  const people = parseTextList(entry.people);
  const relationships = parseTextList(entry.relationships);
  const createdDate = entry.createdAt.toISOString().slice(0, 10);
  const showEntryDate = entry.entryDate !== createdDate;
  const detailPath = `/entries/${entry.id}`;
  const title = entry.title || "未命名记录";

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
      <div className="flex flex-wrap items-center gap-2 text-gray-500 text-xs dark:text-gray-400">
        <LocalDateTime value={entry.createdAt} />
        {showEntryDate ? <time>归属 {entry.entryDate}</time> : null}
        <span>{entryKindLabel(kind)}</span>
        {noteLabel ? <span>{noteLabel}</span> : null}
        {entry.mood ? <span>{MOOD_LABEL[entry.mood]}</span> : null}
        {entry.location ? <span>{entry.location}</span> : null}
        {entry.version > 1 ? (
          <span className="text-gray-400 dark:text-gray-500">
            · 改于 <LocalDateTime value={entry.updatedAt} />
          </span>
        ) : null}
      </div>
      <h2 className="mt-1 font-medium text-gray-950 dark:text-gray-50">
        {openOnCardClick ? (
          <span className="group-hover:underline">{title}</span>
        ) : (
          <Link to={detailPath} className="hover:underline">
            {title}
          </Link>
        )}
      </h2>
      {entry.body ? (
        <p className="mt-1 text-gray-500 text-sm leading-6 dark:text-gray-400">
          {excerpt(entry.body)}
        </p>
      ) : null}
      {showEntryInsight ? (
        <section className="mt-2 rounded-md bg-gray-50 px-3 py-2 text-sm dark:bg-gray-950">
          <EntryInsightControl
            entry={entry}
            compact
            trailing={
              openOnCardClick ? null : (
                <Link
                  to={detailPath}
                  className="text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
                >
                  查看详情
                </Link>
              )
            }
          />
        </section>
      ) : null}
      {people.length > 0 || relationships.length > 0 || entry.tags.length > 0 ? (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {people.map((person) => (
            <span
              key={`person-${person}`}
              className="rounded-full bg-gray-100 px-2 py-0.5 text-gray-500 text-xs dark:bg-gray-800 dark:text-gray-300"
            >
              {person}
            </span>
          ))}
          {relationships.map((relationship) => (
            <span
              key={`relationship-${relationship}`}
              className="rounded-full bg-gray-100 px-2 py-0.5 text-gray-500 text-xs dark:bg-gray-800 dark:text-gray-300"
            >
              {relationship}
            </span>
          ))}
          {entry.tags.map((tag) => (
            <span
              key={`tag-${tag}`}
              className="rounded-full bg-gray-100 px-2 py-0.5 text-gray-500 text-xs dark:bg-gray-800 dark:text-gray-300"
            >
              #{tag}
            </span>
          ))}
        </div>
      ) : null}
    </article>
  );
}
