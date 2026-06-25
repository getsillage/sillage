import type { SummaryPeriodType } from "~/lib/product/summary-fields";

/** Formats a Date as a YYYY-MM-DD calendar date (UTC). */
export function toISODate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

/** Today's date as YYYY-MM-DD (UTC). */
export function todayISO(): string {
  return toISODate(new Date());
}

/** Number of days in a 1-indexed month (month: 1-12). */
export function daysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

/** Weekday (0=Sun..6=Sat) of the first day of a 1-indexed month. */
export function firstWeekday(year: number, month: number): number {
  return new Date(Date.UTC(year, month - 1, 1)).getUTCDay();
}

/** Zero-pads to two digits. */
export function pad2(value: number): string {
  return String(value).padStart(2, "0");
}

/** Whole calendar years from `fromISO` to `toISO` (both YYYY-MM-DD). */
export function yearsBetween(fromISO: string, toISO: string): number {
  return Number(toISO.slice(0, 4)) - Number(fromISO.slice(0, 4));
}

export interface DateRange {
  startDate: string;
  endDate: string;
}

/**
 * Resolves a period type to an inclusive [startDate, endDate] window around a
 * reference date (YYYY-MM-DD), using the same UTC calendar math as the rest of
 * this module. Weeks start on Monday. "custom" has no implicit window — callers
 * supply explicit dates. "all" is resolved by callers against the actual matched
 * entries, so it also falls back to the single reference day here.
 */
export function rangeForPeriod(periodType: SummaryPeriodType, refDateISO: string): DateRange {
  const year = Number(refDateISO.slice(0, 4));
  const month = Number(refDateISO.slice(5, 7)); // 1-12
  const day = Number(refDateISO.slice(8, 10));

  switch (periodType) {
    case "week": {
      const ref = new Date(Date.UTC(year, month - 1, day));
      const daysSinceMonday = (ref.getUTCDay() + 6) % 7;
      const start = new Date(ref);
      start.setUTCDate(ref.getUTCDate() - daysSinceMonday);
      const end = new Date(start);
      end.setUTCDate(start.getUTCDate() + 6);
      return { startDate: toISODate(start), endDate: toISODate(end) };
    }
    case "month":
      return {
        startDate: `${year}-${pad2(month)}-01`,
        endDate: `${year}-${pad2(month)}-${pad2(daysInMonth(year, month))}`,
      };
    case "quarter": {
      const startMonth = Math.floor((month - 1) / 3) * 3 + 1;
      const endMonth = startMonth + 2;
      return {
        startDate: `${year}-${pad2(startMonth)}-01`,
        endDate: `${year}-${pad2(endMonth)}-${pad2(daysInMonth(year, endMonth))}`,
      };
    }
    case "year":
      return { startDate: `${year}-01-01`, endDate: `${year}-12-31` };
    default:
      return { startDate: refDateISO, endDate: refDateISO };
  }
}

/**
 * A gentle relative-time phrase for a past moment, e.g. "刚刚 / 12 分钟前 / 昨天".
 * Bucketed from the millisecond delta (not calendar-aware) so it stays pure and
 * deterministic; falls back to the YYYY-MM-DD date once a moment is over a week old
 * or sits in the future.
 */
export function relativeTime(value: Date, now: Date = new Date()): string {
  const deltaMs = now.getTime() - value.getTime();
  if (deltaMs < 0) {
    return toISODate(value);
  }
  const minutes = Math.floor(deltaMs / 60_000);
  if (minutes < 1) {
    return "刚刚";
  }
  if (minutes < 60) {
    return `${minutes} 分钟前`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours} 小时前`;
  }
  const days = Math.floor(hours / 24);
  if (days === 1) {
    return "昨天";
  }
  if (days < 7) {
    return `${days} 天前`;
  }
  return toISODate(value);
}

/**
 * Builds the calendar grid for a month as rows of 7 cells; cells outside the
 * month are null. Each in-month cell is the YYYY-MM-DD date string.
 */
export function monthGrid(year: number, month: number): (string | null)[][] {
  const total = daysInMonth(year, month);
  const lead = firstWeekday(year, month);
  const cells: (string | null)[] = Array.from({ length: lead }, () => null);
  for (let day = 1; day <= total; day++) {
    cells.push(`${year}-${pad2(month)}-${pad2(day)}`);
  }
  while (cells.length % 7 !== 0) {
    cells.push(null);
  }
  const weeks: (string | null)[][] = [];
  for (let i = 0; i < cells.length; i += 7) {
    weeks.push(cells.slice(i, i + 7));
  }
  return weeks;
}
