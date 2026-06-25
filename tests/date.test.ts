import { describe, expect, it } from "vitest";
import {
  daysInMonth,
  firstWeekday,
  monthGrid,
  pad2,
  relativeTime,
  toISODate,
  yearsBetween,
} from "../app/lib/date";

describe("date helpers", () => {
  it("formats dates as YYYY-MM-DD", () => {
    expect(toISODate(new Date(Date.UTC(2026, 5, 23)))).toBe("2026-06-23");
  });

  it("pads to two digits", () => {
    expect(pad2(6)).toBe("06");
    expect(pad2(12)).toBe("12");
  });

  it("calculates days in month including leap years", () => {
    expect(daysInMonth(2024, 2)).toBe(29);
    expect(daysInMonth(2026, 2)).toBe(28);
  });

  it("calculates the first weekday", () => {
    // 2026-06-01 is Monday.
    expect(firstWeekday(2026, 6)).toBe(1);
  });

  it("computes whole years between two YYYY-MM-DD dates", () => {
    expect(yearsBetween("2024-06-23", "2026-06-23")).toBe(2);
    expect(yearsBetween("2025-06-23", "2026-06-23")).toBe(1);
    expect(yearsBetween("2026-06-23", "2026-06-23")).toBe(0);
  });

  it("builds a calendar grid with null padding", () => {
    const grid = monthGrid(2026, 6);
    expect(grid[0]).toEqual([
      null,
      "2026-06-01",
      "2026-06-02",
      "2026-06-03",
      "2026-06-04",
      "2026-06-05",
      "2026-06-06",
    ]);
    expect(grid.at(-1)?.at(-1)).toBeNull();
  });
});

describe("relativeTime", () => {
  const now = new Date("2026-06-25T12:00:00.000Z");

  it("says 刚刚 within a minute", () => {
    expect(relativeTime(new Date("2026-06-25T11:59:30.000Z"), now)).toBe("刚刚");
  });

  it("counts minutes and hours", () => {
    expect(relativeTime(new Date("2026-06-25T11:48:00.000Z"), now)).toBe("12 分钟前");
    expect(relativeTime(new Date("2026-06-25T09:00:00.000Z"), now)).toBe("3 小时前");
  });

  it("says 昨天 then N 天前", () => {
    expect(relativeTime(new Date("2026-06-24T10:00:00.000Z"), now)).toBe("昨天");
    expect(relativeTime(new Date("2026-06-22T12:00:00.000Z"), now)).toBe("3 天前");
  });

  it("falls back to the date past a week or in the future", () => {
    expect(relativeTime(new Date("2026-06-10T12:00:00.000Z"), now)).toBe("2026-06-10");
    expect(relativeTime(new Date("2026-06-26T12:00:00.000Z"), now)).toBe("2026-06-26");
  });
});
