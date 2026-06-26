import { z } from "zod";
import { SUMMARY_PERIOD_TYPES, SUMMARY_SCOPES, SUMMARY_STYLES } from "~/lib/product/summary-fields";

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export const summaryGenerateSchema = z
  .object({
    scope: z.enum(SUMMARY_SCOPES),
    usePeriod: z.boolean(),
    useTopic: z.boolean(),
    periodType: z.enum(SUMMARY_PERIOD_TYPES).nullable(),
    startDate: z.string().regex(DATE_RE, "日期格式应为 YYYY-MM-DD").nullable(),
    endDate: z.string().regex(DATE_RE, "日期格式应为 YYYY-MM-DD").nullable(),
    style: z.enum(SUMMARY_STYLES),
    filter: z.object({
      keyword: z.string().trim().max(120, "关键词过长"),
    }),
  })
  .superRefine((value, ctx) => {
    if (!value.usePeriod && !value.useTopic) {
      ctx.addIssue({
        code: "custom",
        message: "请至少选择时间范围或主题线索",
        path: ["scope"],
      });
      return;
    }

    if (value.usePeriod) {
      if (!value.periodType) {
        ctx.addIssue({ code: "custom", message: "请选择时间范围", path: ["periodType"] });
      } else if (value.periodType === "custom") {
        if (!value.startDate || !value.endDate) {
          ctx.addIssue({ code: "custom", message: "请填写自定义起止日期", path: ["startDate"] });
        } else if (value.startDate > value.endDate) {
          ctx.addIssue({
            code: "custom",
            message: "起始日期不能晚于结束日期",
            path: ["startDate"],
          });
        }
      }
    }

    const hasFilter = value.filter.keyword.length > 0;
    if (value.useTopic && !hasFilter) {
      ctx.addIssue({
        code: "custom",
        message: "请填写主题关键词",
        path: ["filter"],
      });
    }
  });

export type SummaryGenerateInput = z.infer<typeof summaryGenerateSchema>;

function parseBooleanFlag(value: FormDataEntryValue | null, fallback: boolean): boolean {
  if (value === null) {
    return fallback;
  }
  const normalized = String(value).trim().toLowerCase();
  if (normalized === "auto" || normalized === "") {
    return fallback;
  }
  return normalized === "1" || normalized === "true" || normalized === "on" || normalized === "yes";
}

function filterHasValues(filter: SummaryGenerateInput["filter"]): boolean {
  return filter.keyword.length > 0;
}

/** Extracts the raw generate-summary fields from submitted form data. */
export function summaryGenerateFromData(form: FormData): unknown {
  const scope = String(form.get("scope") ?? "period").trim();
  const periodType = String(form.get("periodType") ?? "").trim();
  const startDate = String(form.get("startDate") ?? "").trim();
  const endDate = String(form.get("endDate") ?? "").trim();
  const filter = {
    keyword: String(form.get("keyword") ?? "").trim(),
  };
  return {
    scope,
    usePeriod: parseBooleanFlag(form.get("usePeriod"), scope !== "topic"),
    useTopic: parseBooleanFlag(form.get("useTopic"), scope === "topic" || filterHasValues(filter)),
    periodType: periodType === "" ? null : periodType,
    startDate: startDate === "" ? null : startDate,
    endDate: endDate === "" ? null : endDate,
    style: String(form.get("style") ?? "brief"),
    filter,
  };
}
