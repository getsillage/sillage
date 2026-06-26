import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { getDb } from "../app/lib/db/client";
import { createEntry, deleteEntry, listEntriesFiltered } from "../app/lib/db/entries";

const db = getDb(env.DB);

async function titles(filter: Parameters<typeof listEntriesFiltered>[1]): Promise<string[]> {
  const rows = await listEntriesFiltered(db, filter);
  return rows.map((row) => row.title);
}

describe("listEntriesFiltered", () => {
  beforeEach(async () => {
    await env.DB.prepare("DELETE FROM entries").run();
    await env.DB.prepare("DELETE FROM tags").run();
    await env.DB.prepare("DELETE FROM entry_tags").run();

    await createEntry(db, {
      entryDate: "2026-06-20",
      title: "记录A",
      body: "海边散步",
      mood: 4,
      people: ["小林"],
      relationships: ["朋友"],
      tags: ["旅行"],
    });
    await createEntry(db, {
      entryDate: "2026-06-22",
      title: "记录B",
      body: "一周回望",
      mood: 3,
      people: ["小林", "妈妈"],
      relationships: ["家人"],
      tags: ["旅行", "家庭"],
    });
    await createEntry(db, {
      entryDate: "2026-06-24",
      title: "记录C",
      body: "还没想清楚",
      mood: 2,
      tags: ["家庭"],
    });
  });

  it("returns everything newest-first with an empty filter", async () => {
    expect(await titles({})).toEqual(["记录C", "记录B", "记录A"]);
  });

  it("filters by mood", async () => {
    expect(await titles({ mood: 2 })).toEqual(["记录C"]);
  });

  it("filters by tag via the entry_tags join", async () => {
    expect(await titles({ tag: "旅行" })).toEqual(["记录B", "记录A"]);
    expect(await titles({ tag: "家庭" })).toEqual(["记录C", "记录B"]);
  });

  it("returns empty for an unknown tag rather than every entry", async () => {
    expect(await titles({ tag: "不存在" })).toEqual([]);
  });

  it("filters by person and relationship against the JSON arrays", async () => {
    expect(await titles({ person: "妈妈" })).toEqual(["记录B"]);
    expect(await titles({ person: "小林" })).toEqual(["记录B", "记录A"]);
    expect(await titles({ relationship: "家人" })).toEqual(["记录B"]);
  });

  it("combines facets as an intersection", async () => {
    expect(await titles({ tag: "旅行", person: "妈妈" })).toEqual(["记录B"]);
    expect(await titles({ tag: "旅行", mood: 5 })).toEqual([]);
  });

  it("excludes soft-deleted entries", async () => {
    const rows = await listEntriesFiltered(db, { tag: "家庭" });
    const entry = rows.find((row) => row.title === "记录C");
    expect(entry).toBeDefined();
    if (entry) {
      await deleteEntry(db, entry.id);
    }
    expect(await titles({ tag: "家庭" })).toEqual(["记录B"]);
  });
});
