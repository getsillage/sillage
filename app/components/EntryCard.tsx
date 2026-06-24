import { Link } from "react-router";
import type { EntryWithTags } from "~/lib/db/entries";
import {
  entryKindLabel,
  normalizeEntryKind,
  normalizeNoteType,
  noteTypeLabel,
  parseTextList,
} from "~/lib/product/entry-fields";
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

export function EntryCard({ entry }: { entry: EntryWithTags }) {
  const kind = normalizeEntryKind(entry.kind);
  const noteLabel = noteTypeLabel(normalizeNoteType(entry.noteType, kind));
  const people = parseTextList(entry.people);
  const relationships = parseTextList(entry.relationships);

  return (
    <Link to={`/entries/${entry.id}`} className={rowLinkClass}>
      <div className="flex flex-wrap items-center gap-2 text-gray-500 text-xs">
        <time>{entry.entryDate}</time>
        <span>{entryKindLabel(kind)}</span>
        {noteLabel ? <span>{noteLabel}</span> : null}
        {entry.mood ? <span>{MOOD_LABEL[entry.mood]}</span> : null}
        {entry.location ? <span>{entry.location}</span> : null}
      </div>
      <h2 className="mt-1 font-medium text-gray-950">{entry.title || "未命名记录"}</h2>
      {entry.body ? (
        <p className="mt-1 text-gray-500 text-sm leading-6">{excerpt(entry.body)}</p>
      ) : null}
      {entry.summary ? (
        <p className="mt-2 rounded-md bg-gray-50 px-3 py-2 text-gray-500 text-sm">
          {entry.summary}
        </p>
      ) : null}
      {people.length > 0 || relationships.length > 0 || entry.tags.length > 0 ? (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {people.map((person) => (
            <span
              key={`person-${person}`}
              className="rounded-full bg-gray-100 px-2 py-0.5 text-gray-500 text-xs"
            >
              {person}
            </span>
          ))}
          {relationships.map((relationship) => (
            <span
              key={`relationship-${relationship}`}
              className="rounded-full bg-gray-100 px-2 py-0.5 text-gray-500 text-xs"
            >
              {relationship}
            </span>
          ))}
          {entry.tags.map((tag) => (
            <span
              key={`tag-${tag}`}
              className="rounded-full bg-gray-100 px-2 py-0.5 text-gray-500 text-xs"
            >
              #{tag}
            </span>
          ))}
        </div>
      ) : null}
    </Link>
  );
}
