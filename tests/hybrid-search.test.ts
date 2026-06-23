import { describe, expect, it } from "vitest";
import type { SearchResult } from "../app/lib/search/fts";
import { mergeSearchResults } from "../app/lib/search/hybrid";

function result(
  id: string,
  source: SearchResult["source"],
  score: number,
  entryDate = "2026-06-23",
): SearchResult {
  return {
    id,
    entryDate,
    title: id,
    body: "",
    mood: null,
    weather: null,
    isPinned: false,
    summary: null,
    sentiment: null,
    createdAt: new Date("2026-06-23T00:00:00Z"),
    updatedAt: new Date("2026-06-23T00:00:00Z"),
    tags: [],
    source,
    score,
  };
}

describe("hybrid search merge", () => {
  it("deduplicates keyword and semantic hits by id", () => {
    const merged = mergeSearchResults(
      [result("a", "keyword", 0.1)],
      [result("a", "semantic", 0.9), result("b", "semantic", 0.8)],
    );

    expect(merged.map((item) => item.id)).toEqual(["a", "b"]);
    expect(merged[0]?.source).toBe("semantic");
  });

  it("falls back to newer entry date for equal scores", () => {
    const merged = mergeSearchResults(
      [result("old", "keyword", 0, "2024-06-23"), result("new", "keyword", 0, "2026-06-23")],
      [],
    );

    expect(merged.map((item) => item.id)).toEqual(["new", "old"]);
  });

  it("respects the result limit", () => {
    const merged = mergeSearchResults(
      [result("a", "keyword", 0), result("b", "keyword", 0), result("c", "keyword", 0)],
      [],
      2,
    );

    expect(merged).toHaveLength(2);
  });
});
