import { describe, expect, it } from "vitest";
import type { Memo } from "./api";
import {
  entriesByDate,
  entryDateCounts,
  excerpt,
  isActive,
  onThisDay,
  sortMemos,
  upsertMemo,
} from "./memos";

function memo(overrides: Partial<Memo> = {}): Memo {
  return {
    id: "m1",
    content: "hello",
    entryDate: "2026-06-27",
    version: 1,
    favoritedAt: null,
    archivedAt: null,
    createdAt: "2026-06-27T08:00:00Z",
    updatedAt: "2026-06-27T08:00:00Z",
    deletedAt: null,
    ...overrides,
  };
}

describe("excerpt", () => {
  it("collapses whitespace and truncates with an ellipsis", () => {
    expect(excerpt("a\n\n  b   c")).toBe("a b c");
    expect(excerpt("abcdef", 3)).toBe("abc…");
    expect(excerpt("abc", 3)).toBe("abc");
  });
});

describe("sortMemos", () => {
  it("orders by entry date desc, then created desc", () => {
    const a = memo({ id: "a", entryDate: "2026-06-20" });
    const b = memo({ id: "b", entryDate: "2026-06-27" });
    const favorite = memo({
      id: "f",
      entryDate: "2026-01-01",
      favoritedAt: "2026-06-27T00:00:00Z",
    });
    const sorted = sortMemos([a, b, favorite]);
    expect(sorted.map((m) => m.id)).toEqual(["b", "a", "f"]);
  });

  it("does not mutate the input", () => {
    const input = [memo({ id: "a" }), memo({ id: "b" })];
    const snapshot = [...input];
    sortMemos(input);
    expect(input).toEqual(snapshot);
  });
});

describe("upsertMemo", () => {
  it("replaces by id and appends new ones immutably", () => {
    const list = [memo({ id: "a", content: "old" })];
    const replaced = upsertMemo(list, memo({ id: "a", content: "new" }));
    expect(replaced).toHaveLength(1);
    expect(replaced[0].content).toBe("new");
    expect(list[0].content).toBe("old"); // original untouched

    const appended = upsertMemo(list, memo({ id: "b" }));
    expect(appended.map((m) => m.id)).toEqual(["a", "b"]);
  });
});

describe("isActive", () => {
  it("excludes archived, favorited, and deleted records", () => {
    expect(isActive(memo())).toBe(true);
    expect(isActive(memo({ archivedAt: "x" }))).toBe(false);
    expect(isActive(memo({ favoritedAt: "x" }))).toBe(false);
    expect(isActive(memo({ deletedAt: "x" }))).toBe(false);
  });
});

describe("onThisDay", () => {
  it("returns active memos on the same month/day in earlier years, newest first", () => {
    const todayISO = "2026-06-27";
    const memos = [
      memo({ id: "this-year", entryDate: "2026-06-27" }),
      memo({ id: "2024", entryDate: "2024-06-27" }),
      memo({ id: "2025", entryDate: "2025-06-27" }),
      memo({ id: "other-day", entryDate: "2025-06-26" }),
      memo({ id: "archived", entryDate: "2023-06-27", archivedAt: "x" }),
    ];
    expect(onThisDay(memos, todayISO).map((m) => m.id)).toEqual([
      "2025",
      "2024",
    ]);
  });
});

describe("entryDateCounts / entriesByDate", () => {
  it("counts and groups only active memos by entry date", () => {
    const memos = [
      memo({ id: "a", entryDate: "2026-06-27" }),
      memo({ id: "b", entryDate: "2026-06-27" }),
      memo({ id: "c", entryDate: "2026-06-26" }),
      memo({ id: "d", entryDate: "2026-06-27", deletedAt: "x" }),
    ];
    expect(entryDateCounts(memos)).toEqual({
      "2026-06-27": 2,
      "2026-06-26": 1,
    });
    expect(entriesByDate(memos, "2026-06-27").map((m) => m.id)).toEqual([
      "a",
      "b",
    ]);
  });
});
