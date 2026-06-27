import { describe, expect, it } from "vitest";
import {
  daysInMonth,
  firstWeekday,
  monthGrid,
  normalizeYearMonth,
  pad2,
  toLocalISODate,
  yearsBetween,
} from "./date";

describe("pad2", () => {
  it("zero-pads single digits", () => {
    expect(pad2(3)).toBe("03");
    expect(pad2(12)).toBe("12");
  });
});

describe("toLocalISODate", () => {
  it("formats a local date as YYYY-MM-DD", () => {
    // Construct via local components to avoid timezone ambiguity.
    expect(toLocalISODate(new Date(2026, 0, 5))).toBe("2026-01-05");
    expect(toLocalISODate(new Date(2026, 11, 31))).toBe("2026-12-31");
  });
});

describe("daysInMonth", () => {
  it("handles leap years", () => {
    expect(daysInMonth(2024, 2)).toBe(29);
    expect(daysInMonth(2026, 2)).toBe(28);
    expect(daysInMonth(2026, 4)).toBe(30);
    expect(daysInMonth(2026, 1)).toBe(31);
  });
});

describe("firstWeekday", () => {
  it("returns 0..6 for the first of a month", () => {
    // 2026-06-01 is a Monday.
    expect(firstWeekday(2026, 6)).toBe(1);
  });
});

describe("normalizeYearMonth", () => {
  it("normalizes out-of-range 1-indexed months", () => {
    expect(normalizeYearMonth(2026, 13)).toEqual({ year: 2027, month: 1 });
    expect(normalizeYearMonth(2026, 0)).toEqual({ year: 2025, month: 12 });
  });
});

describe("yearsBetween", () => {
  it("counts whole calendar years by the year prefix", () => {
    expect(yearsBetween("2020-06-27", "2026-06-27")).toBe(6);
    expect(yearsBetween("2026-01-01", "2026-12-31")).toBe(0);
  });
});

describe("monthGrid", () => {
  it("pads to full weeks with nulls and places dates", () => {
    const weeks = monthGrid(2026, 6); // June 2026, starts Monday
    expect(weeks.every((week) => week.length === 7)).toBe(true);
    // First day (Mon) sits at index 1; Sunday lead cell is null.
    expect(weeks[0][0]).toBeNull();
    expect(weeks[0][1]).toBe("2026-06-01");
    const flat = weeks.flat().filter(Boolean);
    expect(flat).toHaveLength(30);
    expect(flat[flat.length - 1]).toBe("2026-06-30");
  });
});
