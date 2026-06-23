import { describe, expect, it } from "vitest";
import { daysInMonth, firstWeekday, monthGrid, pad2, toISODate } from "../app/lib/date";

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
