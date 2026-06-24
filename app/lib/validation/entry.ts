import { z } from "zod";
import {
  cleanTextList,
  ENTRY_KINDS,
  type EntryKind,
  REFLECTION_TYPES,
  type ReflectionType,
} from "~/lib/product/entry-fields";

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

const kindSchema = z.enum(ENTRY_KINDS);
const reflectionTypeSchema = z.enum(REFLECTION_TYPES);

const baseEntrySchema = z
  .object({
    entryDate: z.string().regex(DATE_RE, "日期格式应为 YYYY-MM-DD"),
    title: z.string().trim().max(200, "标题过长"),
    body: z.string().max(50_000, "内容过长"),
    mood: z.number().int().min(1).max(5).nullable(),
    moodText: z.string().trim().max(120, "细腻感受过长").nullable(),
    weather: z.string().trim().max(50, "天气过长").nullable(),
    location: z.string().trim().max(120, "地点过长").nullable(),
    kind: kindSchema,
    reflectionType: reflectionTypeSchema.nullable(),
    people: z.array(z.string().trim().max(80, "人物名称过长")),
    relationships: z.array(z.string().trim().max(80, "关系描述过长")),
    tags: z.array(z.string()),
  })
  .refine((value) => value.title.length > 0 || value.body.length > 0, {
    message: "标题或内容至少填写一项",
    path: ["body"],
  });

export const entrySchema = baseEntrySchema.transform((value) => ({
  entryDate: value.entryDate,
  title: value.title,
  body: value.body,
  kind: value.kind,
  reflectionType: value.reflectionType,
  mood: value.mood,
  moodText: value.moodText,
  weather: value.weather,
  location: value.location,
  people: cleanTextList(value.people),
  relationships: cleanTextList(value.relationships),
  tags: value.tags,
}));

export type EntryFormValues = z.input<typeof entrySchema>;
export type EntrySubmitValues = z.output<typeof entrySchema>;

function parseDelimited(raw: string): string[] {
  return raw
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function nullableText(form: FormData, key: string): string | null {
  const value = String(form.get(key) ?? "").trim();
  return value === "" ? null : value;
}

function parseKind(raw: FormDataEntryValue | null): EntryKind {
  return ENTRY_KINDS.includes(raw as EntryKind) ? (raw as EntryKind) : "fragment";
}

function parseReflectionType(
  raw: FormDataEntryValue | null,
  kind: EntryKind,
): ReflectionType | null {
  if (kind !== "reflection") {
    return null;
  }
  return REFLECTION_TYPES.includes(raw as ReflectionType) ? (raw as ReflectionType) : "daily";
}

/** Extracts raw entry fields from submitted form data (before validation). */
export function entryFormFromData(form: FormData): EntryFormValues {
  const moodRaw = form.get("mood");
  const mood =
    moodRaw && moodRaw !== "" && Number.isFinite(Number(moodRaw)) ? Number(moodRaw) : null;
  const kind = parseKind(form.get("kind"));
  return {
    entryDate: String(form.get("entryDate") ?? ""),
    title: String(form.get("title") ?? ""),
    body: String(form.get("body") ?? ""),
    mood,
    moodText: nullableText(form, "moodText"),
    weather: nullableText(form, "weather"),
    location: nullableText(form, "location"),
    kind,
    reflectionType: parseReflectionType(form.get("reflectionType"), kind),
    people: parseDelimited(String(form.get("people") ?? "")),
    relationships: parseDelimited(String(form.get("relationships") ?? "")),
    tags: parseDelimited(String(form.get("tags") ?? "")),
  };
}
