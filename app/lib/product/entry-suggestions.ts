import type { EntryWithTags } from "~/lib/db/entries";
import { cleanTextList, parseTextList } from "./entry-fields";

export interface EntryFormSuggestions {
  locations: string[];
  people: string[];
  relationships: string[];
  tags: string[];
}

export const STANDARD_RELATIONSHIPS = [
  "自己",
  "朋友",
  "家人",
  "伴侣",
  "同事",
  "同学",
  "陌生人",
] as const;

export function buildEntryFormSuggestions(entries: EntryWithTags[]): EntryFormSuggestions {
  return {
    locations: cleanTextList(entries.map((entry) => entry.location)),
    people: cleanTextList(entries.flatMap((entry) => parseTextList(entry.people))),
    relationships: cleanTextList([
      ...STANDARD_RELATIONSHIPS,
      ...entries.flatMap((entry) => parseTextList(entry.relationships)),
    ]),
    tags: cleanTextList(entries.flatMap((entry) => entry.tags)),
  };
}
