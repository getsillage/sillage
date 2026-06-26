import { z } from "zod";
import {
  ENTRY_KINDS,
  type EntryKind,
  NOTE_TYPES,
  type NoteType,
  normalizeNoteType,
} from "~/lib/product/entry-fields";

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

const kindSchema = z.enum(ENTRY_KINDS);
const baseEntrySchema = z
  .object({
    entryDate: z.string().regex(DATE_RE, "日期格式应为 YYYY-MM-DD"),
    body: z.string().max(50_000, "内容过长"),
    kind: kindSchema,
    noteType: z.enum(NOTE_TYPES).nullable(),
  })
  .refine((value) => value.body.trim().length > 0, {
    message: "请写下一些内容",
    path: ["body"],
  });

export const entrySchema = baseEntrySchema.transform((value) => ({
  entryDate: value.entryDate,
  title: "",
  body: value.body,
  kind: value.kind,
  noteType: normalizeNoteType(value.noteType, value.kind),
  mood: null,
  moodText: null,
  weather: null,
  location: null,
  people: [],
  relationships: [],
  tags: [],
}));

export type EntryFormValues = z.input<typeof entrySchema>;
export type EntrySubmitValues = z.output<typeof entrySchema>;

function parseKind(raw: FormDataEntryValue | null): EntryKind {
  return ENTRY_KINDS.includes(raw as EntryKind) ? (raw as EntryKind) : "fragment";
}

function parseNoteType(raw: FormDataEntryValue | null, kind: EntryKind): NoteType | null {
  if (kind !== "note") {
    return null;
  }
  return NOTE_TYPES.includes(raw as NoteType) ? (raw as NoteType) : "daily";
}

/** Extracts raw entry fields from submitted form data (before validation). */
export function entryFormFromData(form: FormData): EntryFormValues {
  const kind = parseKind(form.get("kind"));
  return {
    entryDate: String(form.get("entryDate") ?? ""),
    body: String(form.get("body") ?? ""),
    kind,
    noteType: parseNoteType(form.get("noteType"), kind),
  };
}
