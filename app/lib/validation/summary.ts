import { z } from "zod";
import { SUMMARY_PERIOD_TYPES, SUMMARY_SCOPES, SUMMARY_STYLES } from "~/lib/product/summary-fields";

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function parseDelimited(raw: string): string[] {
  return raw
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export const summaryGenerateSchema = z
  .object({
    scope: z.enum(SUMMARY_SCOPES),
    periodType: z.enum(SUMMARY_PERIOD_TYPES).nullable(),
    startDate: z.string().regex(DATE_RE, "日期格式应为 YYYY-MM-DD").nullable(),
    endDate: z.string().regex(DATE_RE, "日期格式应为 YYYY-MM-DD").nullable(),
    style: z.enum(SUMMARY_STYLES),
    filter: z.object({
      tags: z.array(z.string().trim().max(80, "标签过长")),
      people: z.array(z.string().trim().max(80, "人物名称过长")),
      relationships: z.array(z.string().trim().max(80, "关系描述过长")),
      keyword: z.string().trim().max(120, "关键词过长"),
      entryIds: z.array(z.string().trim()),
    }),
  })
  .superRefine((value, ctx) => {
    if (value.scope === "period") {
      if (!value.periodType) {
        ctx.addIssue({ code: "custom", message: "请选择时间范围", path: ["periodType"] });
        return;
      }
      if (value.periodType === "custom") {
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
      return;
    }

    const { tags, people, relationships, keyword, entryIds } = value.filter;
    const hasFilter =
      tags.length > 0 ||
      people.length > 0 ||
      relationships.length > 0 ||
      keyword.length > 0 ||
      entryIds.length > 0;
    if (!hasFilter) {
      ctx.addIssue({
        code: "custom",
        message: "请至少选择一个标签、人物、关键词或记录",
        path: ["filter"],
      });
    }
  });

export type SummaryGenerateInput = z.infer<typeof summaryGenerateSchema>;

/** Extracts the raw generate-summary fields from submitted form data. */
export function summaryGenerateFromData(form: FormData): unknown {
  const periodType = String(form.get("periodType") ?? "").trim();
  const startDate = String(form.get("startDate") ?? "").trim();
  const endDate = String(form.get("endDate") ?? "").trim();
  return {
    scope: String(form.get("scope") ?? "period"),
    periodType: periodType === "" ? null : periodType,
    startDate: startDate === "" ? null : startDate,
    endDate: endDate === "" ? null : endDate,
    style: String(form.get("style") ?? "brief"),
    filter: {
      tags: parseDelimited(String(form.get("tags") ?? "")),
      people: parseDelimited(String(form.get("people") ?? "")),
      relationships: parseDelimited(String(form.get("relationships") ?? "")),
      keyword: String(form.get("keyword") ?? "").trim(),
      entryIds: parseDelimited(String(form.get("entryIds") ?? "")),
    },
  };
}
