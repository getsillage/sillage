import { z } from "zod";

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export const entrySchema = z
  .object({
    entryDate: z.string().regex(DATE_RE, "日期格式应为 YYYY-MM-DD"),
    title: z.string().trim().max(200, "标题过长"),
    body: z.string().max(50_000, "内容过长"),
    mood: z.number().int().min(1).max(5).nullable(),
    weather: z.string().trim().max(50, "天气过长").nullable(),
    tags: z.array(z.string()),
  })
  .refine((value) => value.title.length > 0 || value.body.length > 0, {
    message: "标题或内容至少填写一项",
    path: ["body"],
  });

export type EntryFormValues = z.infer<typeof entrySchema>;

function parseTags(raw: string): string[] {
  return raw
    .split(/[,，\s]+/)
    .map((tag) => tag.trim())
    .filter(Boolean);
}

/** Extracts raw entry fields from submitted form data (before validation). */
export function entryFormFromData(form: FormData): EntryFormValues {
  const moodRaw = form.get("mood");
  const mood =
    moodRaw && moodRaw !== "" && Number.isFinite(Number(moodRaw)) ? Number(moodRaw) : null;
  const weather = String(form.get("weather") ?? "").trim();
  return {
    entryDate: String(form.get("entryDate") ?? ""),
    title: String(form.get("title") ?? ""),
    body: String(form.get("body") ?? ""),
    mood,
    weather: weather === "" ? null : weather,
    tags: parseTags(String(form.get("tags") ?? "")),
  };
}
