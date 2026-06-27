/** Formats a Date as a YYYY-MM-DD calendar date in the viewer's local zone. */
export function toLocalISODate(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

/** Today's date as YYYY-MM-DD (local). */
export function todayISO(): string {
  return toLocalISODate(new Date());
}

/** Number of days in a 1-indexed month (month: 1-12). */
export function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate();
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
 * A gentle relative-time phrase for a past moment, e.g. "刚刚 / 12 分钟前 / 昨天".
 * Falls back to the YYYY-MM-DD date once a moment is over a week old or sits in
 * the future.
 */
export function relativeTime(value: Date, now: Date = new Date()): string {
  const deltaMs = now.getTime() - value.getTime();
  if (deltaMs < 0) {
    return toLocalISODate(value);
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
  return toLocalISODate(value);
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
