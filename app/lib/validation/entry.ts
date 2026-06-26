import { z } from "zod";

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

const baseEntrySchema = z
  .object({
    entryDate: z.string().regex(DATE_RE, "日期格式应为 YYYY-MM-DD"),
    body: z.string().max(50_000, "内容过长"),
  })
  .refine((value) => value.body.trim().length > 0, {
    message: "请写下一些内容",
    path: ["body"],
  });

export const entrySchema = baseEntrySchema.transform((value) => ({
  entryDate: value.entryDate,
  body: value.body,
}));

export type EntryFormValues = z.input<typeof entrySchema>;
export type EntrySubmitValues = z.output<typeof entrySchema>;

/** Extracts raw entry fields from submitted form data (before validation). */
export function entryFormFromData(form: FormData): EntryFormValues {
  return {
    entryDate: String(form.get("entryDate") ?? ""),
    body: String(form.get("body") ?? ""),
  };
}
