import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { getDb } from "../app/lib/db/client";
import {
  createEntry,
  deleteEntry,
  getEntry,
  listEntries,
  updateEntry,
} from "../app/lib/db/entries";

const db = getDb(env.DB);

describe("entries repository", () => {
  beforeEach(async () => {
    await env.DB.prepare("DELETE FROM entries").run();
    await env.DB.prepare("DELETE FROM tags").run();
  });

  it("creates an entry with normalized tags", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "标题",
      body: "正文内容",
      mood: 4,
      weather: "晴",
      tags: ["旅行", " 旅行 ", "美食", ""],
    });

    const entry = await getEntry(db, id);
    expect(entry?.title).toBe("标题");
    expect(entry?.mood).toBe(4);
    expect(entry?.tags).toEqual(["旅行", "美食"]); // sorted + deduped
  });

  it("updates content and replaces the tag set", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "原标题",
      body: "原内容",
      tags: ["a", "b"],
    });

    const ok = await updateEntry(db, id, {
      entryDate: "2026-06-24",
      title: "新标题",
      body: "新内容",
      mood: null,
      weather: null,
      tags: ["b", "c"],
    });

    expect(ok).toBe(true);
    const entry = await getEntry(db, id);
    expect(entry?.title).toBe("新标题");
    expect(entry?.entryDate).toBe("2026-06-24");
    expect(entry?.tags).toEqual(["b", "c"]);
  });

  it("returns false when updating a missing entry", async () => {
    const ok = await updateEntry(db, "does-not-exist", {
      entryDate: "2026-06-23",
      title: "x",
      body: "y",
      mood: null,
      weather: null,
      tags: [],
    });
    expect(ok).toBe(false);
  });

  it("lists entries newest-first by entry date", async () => {
    await createEntry(db, {
      entryDate: "2026-06-20",
      title: "早",
      body: "",
      tags: [],
    });
    await createEntry(db, {
      entryDate: "2026-06-23",
      title: "晚",
      body: "",
      tags: [],
    });

    const list = await listEntries(db);
    expect(list.map((e) => e.title)).toEqual(["晚", "早"]);
  });

  it("deletes an entry and cascades its tags link", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "待删除",
      body: "内容",
      tags: ["临时"],
    });
    await deleteEntry(db, id);

    expect(await getEntry(db, id)).toBeNull();
    const { results } = await env.DB.prepare("SELECT count(*) AS n FROM entry_tags").all<{
      n: number;
    }>();
    expect(results[0]?.n).toBe(0);
  });
});
