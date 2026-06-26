import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { getDb } from "../app/lib/db/client";
import { createEntry, deleteEntry, restoreEntry } from "../app/lib/db/entries";
import { searchEntriesByKeyword } from "../app/lib/search/fts";

const db = getDb(env.DB);

describe("keyword search", () => {
  beforeEach(async () => {
    await env.DB.prepare("DELETE FROM entries").run();
  });

  it("finds Chinese text from body using trigram FTS", async () => {
    await createEntry(db, {
      entryDate: "2026-06-23",
      body: "今天去了海边，看到很漂亮的夕阳。",
    });
    await createEntry(db, {
      entryDate: "2026-06-24",
      body: "整理了一些技术笔记。",
    });

    const results = await searchEntriesByKeyword(db, "漂亮的夕阳");
    expect(results).toHaveLength(1);
    expect(results[0]?.body).toContain("漂亮的夕阳");
    expect(results[0]?.source).toBe("keyword");
  });

  it("returns an empty list for blank queries", async () => {
    expect(await searchEntriesByKeyword(db, "   ")).toEqual([]);
  });

  it("drops soft-deleted entries from the index and re-adds on restore", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      body: "今天去了海边，看到很漂亮的夕阳。",
    });

    expect(await searchEntriesByKeyword(db, "漂亮的夕阳")).toHaveLength(1);

    await deleteEntry(db, id);
    expect(await searchEntriesByKeyword(db, "漂亮的夕阳")).toHaveLength(0);

    await restoreEntry(db, id);
    expect(await searchEntriesByKeyword(db, "漂亮的夕阳")).toHaveLength(1);
  });

  it("handles quotes in user queries safely", async () => {
    await createEntry(db, {
      entryDate: "2026-06-23",
      body: '今天记录了 "special" 这个词。',
    });

    const results = await searchEntriesByKeyword(db, '"special"');
    expect(results.map((entry) => entry.body)).toEqual(['今天记录了 "special" 这个词。']);
  });

  it("falls back to field matching for long natural-language queries", async () => {
    await createEntry(db, {
      entryDate: "2026-06-23",
      body: "系统里的今天是 2026-06-24。",
    });

    const results = await searchEntriesByKeyword(
      db,
      "不要根据记录，我问的是你系统里指定的今天是什么时间",
    );

    expect(results.map((entry) => entry.body)).toEqual(["系统里的今天是 2026-06-24。"]);
  });
});
