import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { getDb } from "../app/lib/db/client";
import {
  createEntry,
  deleteEntry,
  getEntry,
  listEntries,
  purgeEntry,
  restoreEntry,
  updateEntry,
} from "../app/lib/db/entries";
import { parseTextList } from "../app/lib/product/entry-fields";

async function readMeta(id: string): Promise<{ off: number | null; metadata: string | null }> {
  const { results } = await env.DB.prepare(
    "SELECT utc_offset_minutes AS off, metadata FROM entries WHERE id = ?",
  )
    .bind(id)
    .all<{ off: number | null; metadata: string | null }>();
  return results[0] ?? { off: null, metadata: null };
}

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
      moodText: "轻松但有一点想念",
      weather: "晴",
      location: "海边",
      people: ["朋友", "朋友", ""],
      relationships: ["朋友"],
      tags: ["旅行", " 旅行 ", "美食", ""],
    });

    const entry = await getEntry(db, id);
    expect(entry?.title).toBe("标题");
    expect(entry?.mood).toBe(4);
    expect(entry?.moodText).toBe("轻松但有一点想念");
    expect(entry?.location).toBe("海边");
    expect(parseTextList(entry?.people)).toEqual(["朋友"]);
    expect(parseTextList(entry?.relationships)).toEqual(["朋友"]);
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
      people: ["新朋友"],
      relationships: ["同事"],
      tags: ["b", "c"],
    });

    expect(ok.status).toBe("updated");
    const entry = await getEntry(db, id);
    expect(entry?.title).toBe("新标题");
    expect(entry?.entryDate).toBe("2026-06-24");
    expect(parseTextList(entry?.people)).toEqual(["新朋友"]);
    expect(parseTextList(entry?.relationships)).toEqual(["同事"]);
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
    expect(ok).toEqual({ status: "missing" });
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

  it("soft-deletes an entry: hidden from reads but tombstoned for sync/undo", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "待删除",
      body: "内容",
      tags: ["临时"],
    });
    await deleteEntry(db, id);

    // Hidden from normal reads.
    expect(await getEntry(db, id)).toBeNull();
    expect(await listEntries(db)).toHaveLength(0);

    // Row remains with a tombstone, and its tag link is preserved for undo.
    const { results } = await env.DB.prepare(
      "SELECT deleted_at AS deletedAt FROM entries WHERE id = ?",
    )
      .bind(id)
      .all<{ deletedAt: number | null }>();
    expect(results[0]?.deletedAt).not.toBeNull();
    const { results: links } = await env.DB.prepare(
      "SELECT count(*) AS n FROM entry_tags WHERE entry_id = ?",
    )
      .bind(id)
      .all<{ n: number }>();
    expect(links[0]?.n).toBe(1);
  });

  it("restores a soft-deleted entry", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "可恢复",
      body: "内容",
      tags: [],
    });
    await deleteEntry(db, id);
    await restoreEntry(db, id);

    const entry = await getEntry(db, id);
    expect(entry?.title).toBe("可恢复");
  });

  it("rejects a stale update with a version conflict", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "原标题",
      body: "原内容",
      tags: [],
    });

    const first = await updateEntry(
      db,
      id,
      { entryDate: "2026-06-23", title: "改一次", body: "x", mood: null, weather: null, tags: [] },
      1,
    );
    expect(first).toEqual({ status: "updated", version: 2 });

    // A second writer still holding version 1 is rejected, not silently merged.
    const stale = await updateEntry(
      db,
      id,
      {
        entryDate: "2026-06-23",
        title: "并发覆盖",
        body: "y",
        mood: null,
        weather: null,
        tags: [],
      },
      1,
    );
    expect(stale).toEqual({ status: "conflict", currentVersion: 2 });

    const entry = await getEntry(db, id);
    expect(entry?.title).toBe("改一次");
  });

  it("stores client metadata/offset and never wipes them on a partial update", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "带元数据",
      body: "x",
      tags: [],
      utcOffsetMinutes: 480,
      metadata: { client: "ios", draft: true },
    });
    const stored = await readMeta(id);
    expect(stored.off).toBe(480);
    expect(JSON.parse(stored.metadata ?? "null")).toEqual({ client: "ios", draft: true });

    // A web-style update omits these keys → they must be preserved.
    await updateEntry(db, id, {
      entryDate: "2026-06-23",
      title: "改标题",
      body: "y",
      mood: null,
      weather: null,
      tags: [],
    });
    const preserved = await readMeta(id);
    expect(preserved.off).toBe(480);
    expect(preserved.metadata).not.toBeNull();

    // An explicit update with the keys present overwrites them.
    await updateEntry(db, id, {
      entryDate: "2026-06-23",
      title: "再改",
      body: "z",
      mood: null,
      weather: null,
      tags: [],
      utcOffsetMinutes: -300,
      metadata: { client: "web" },
    });
    const overwritten = await readMeta(id);
    expect(overwritten.off).toBe(-300);
    expect(JSON.parse(overwritten.metadata ?? "null")).toEqual({ client: "web" });
  });

  it("purges an entry permanently, cascading its tag links", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "永久删除",
      body: "x",
      tags: ["临时"],
    });
    await purgeEntry(db, id);

    const { results } = await env.DB.prepare("SELECT count(*) AS n FROM entries WHERE id = ?")
      .bind(id)
      .all<{ n: number }>();
    expect(results[0]?.n).toBe(0);
    const { results: links } = await env.DB.prepare(
      "SELECT count(*) AS n FROM entry_tags WHERE entry_id = ?",
    )
      .bind(id)
      .all<{ n: number }>();
    expect(links[0]?.n).toBe(0);
  });
});
