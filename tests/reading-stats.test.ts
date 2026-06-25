import { describe, expect, it } from "vitest";
import { formatReadingStats, readingStats } from "../app/lib/product/reading-stats";

describe("readingStats", () => {
  it("counts CJK characters", () => {
    const stats = readingStats("今天天气很好");
    expect(stats.chars).toBe(6);
    expect(stats.minutes).toBe(1);
  });

  it("counts latin words as roughly five characters each", () => {
    expect(readingStats("hello world").chars).toBe(10);
  });

  it("returns zero for blank input", () => {
    expect(readingStats("   ")).toEqual({ chars: 0, minutes: 0 });
    expect(formatReadingStats(readingStats(""))).toBe("");
  });

  it("estimates minutes from the reading pace", () => {
    expect(readingStats("字".repeat(800)).minutes).toBe(2);
  });

  it("formats a friendly one-liner", () => {
    expect(formatReadingStats({ chars: 320, minutes: 1 })).toBe("约 320 字 · 1 分钟读完");
  });
});
