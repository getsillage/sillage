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
