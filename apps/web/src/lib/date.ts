import { type Locale, translate } from "../i18n/messages";

/** Formats a Date as a YYYY-MM-DD calendar date in the viewer's local zone. */
export function toLocalISODate(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

/** Today's date as YYYY-MM-DD (local). */
export function todayISO(): string {
  return toLocalISODate(new Date());
}

function parseISODate(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) {
    return null;
  }
  const date = new Date(
    Number(match[1]),
    Number(match[2]) - 1,
    Number(match[3]),
  );
  return Number.isNaN(date.getTime()) ? null : date;
}

/** Human-friendly label for a stored YYYY-MM-DD entry date. */
export function formatEntryDate(
  value: string,
  reference = todayISO(),
  locale: Locale = "zh-CN",
): string {
  const date = parseISODate(value);
  const referenceDate = parseISODate(reference);
  if (!date || !referenceDate) {
    return value;
  }

  const dayMs = 24 * 60 * 60 * 1000;
  const dateDay = Date.UTC(date.getFullYear(), date.getMonth(), date.getDate());
  const referenceDay = Date.UTC(
    referenceDate.getFullYear(),
    referenceDate.getMonth(),
    referenceDate.getDate(),
  );
  const daysAgo = Math.round((referenceDay - dateDay) / dayMs);
  const includeYear = date.getFullYear() !== referenceDate.getFullYear();
  const calendarLabel =
    locale === "zh-CN"
      ? `${new Intl.DateTimeFormat(locale, {
          month: "long",
          day: "numeric",
          year: includeYear ? "numeric" : undefined,
        }).format(date)} ${new Intl.DateTimeFormat(locale, {
          weekday: "short",
        }).format(date)}`
      : new Intl.DateTimeFormat(locale, {
          weekday: "short",
          month: "long",
          day: "numeric",
          year: includeYear ? "numeric" : undefined,
        }).format(date);

  if (daysAgo === 0) {
    return `${translate(locale, "date.today")}${locale === "zh-CN" ? "，" : ", "}${calendarLabel}`;
  }
  if (daysAgo === 1) {
    return `${translate(locale, "date.yesterday")}${locale === "zh-CN" ? "，" : ", "}${calendarLabel}`;
  }
  return calendarLabel;
}

/** Compact YYYY-MM-DD label for inline metadata. */
export function formatShortDate(
  value: string,
  reference = todayISO(),
  locale: Locale = "zh-CN",
): string {
  const date = parseISODate(value);
  const referenceDate = parseISODate(reference);
  if (!date || !referenceDate) {
    return value;
  }
  const includeYear = date.getFullYear() !== referenceDate.getFullYear();
  return new Intl.DateTimeFormat(locale, {
    month: "long",
    day: "numeric",
    year: includeYear ? "numeric" : undefined,
  }).format(date);
}

export function formatMonth(
  year: number,
  month: number,
  locale: Locale = "zh-CN",
): string {
  return new Intl.DateTimeFormat(locale, {
    year: "numeric",
    month: "long",
  }).format(new Date(year, month - 1, 1));
}

export function formatWeekday(
  weekday: number,
  locale: Locale = "zh-CN",
): string {
  return new Intl.DateTimeFormat(locale, { weekday: "narrow" }).format(
    new Date(2024, 0, 7 + weekday),
  );
}

/** Number of days in a 1-indexed month (month: 1-12). */
export function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate();
}

/** Normalizes a possibly out-of-range 1-indexed month into a real year/month. */
export function normalizeYearMonth(
  year: number,
  month: number,
): { year: number; month: number } {
  const normalized = new Date(year, month - 1, 1);
  return {
    year: normalized.getFullYear(),
    month: normalized.getMonth() + 1,
  };
}

/** Weekday (0=Sun..6=Sat) of the first day of a 1-indexed month. */
export function firstWeekday(year: number, month: number): number {
  return new Date(year, month - 1, 1).getDay();
}

/** Zero-pads to two digits. */
export function pad2(value: number): string {
  return String(value).padStart(2, "0");
}

/** Whole calendar years from `fromISO` to `toISO` (both YYYY-MM-DD). */
export function yearsBetween(fromISO: string, toISO: string): number {
  return Number(toISO.slice(0, 4)) - Number(fromISO.slice(0, 4));
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
