/**
 * Vocabulary for AI reviews (the `summaries` table): one source of truth for
 * scopes / period types / styles and their Chinese labels.
 */

export const SUMMARY_SCOPES = ["period", "topic"] as const;
export type SummaryScope = (typeof SUMMARY_SCOPES)[number];

export const SUMMARY_PERIOD_TYPES = [
  "all",
  "day",
  "week",
  "month",
  "quarter",
  "year",
  "custom",
] as const;
export type SummaryPeriodType = (typeof SUMMARY_PERIOD_TYPES)[number];

export const SUMMARY_STYLES = ["brief", "structured", "narrative"] as const;
export type SummaryStyle = (typeof SUMMARY_STYLES)[number];

export const PERIOD_TYPE_LABELS: Record<SummaryPeriodType, string> = {
  all: "全部时间",
  day: "当日",
  week: "本周",
  month: "本月",
  quarter: "本季",
  year: "全年",
  custom: "自定义",
};

export const STYLE_LABELS: Record<SummaryStyle, string> = {
  brief: "简短摘要",
  structured: "结构化总结",
  narrative: "叙述长文",
};

/** The set of records a topic review is woven from. */
export interface SummaryFilter {
  keyword?: string;
}

export function isSummaryScope(value: string): value is SummaryScope {
  return (SUMMARY_SCOPES as readonly string[]).includes(value);
}

export function isSummaryPeriodType(value: string): value is SummaryPeriodType {
  return (SUMMARY_PERIOD_TYPES as readonly string[]).includes(value);
}

export function isSummaryStyle(value: string): value is SummaryStyle {
  return (SUMMARY_STYLES as readonly string[]).includes(value);
}
