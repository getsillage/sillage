import { env } from "cloudflare:test";
import { eq } from "drizzle-orm";
import { beforeEach, describe, expect, it } from "vitest";
import { getDb } from "../app/lib/db/client";
import { entries } from "../app/lib/db/schema";

async function ftsMatch(query: string): Promise<string[]> {
  const { results } = await env.DB.prepare(
    "SELECT e.id AS id FROM entries e JOIN entries_fts f ON f.rowid = e.rowid WHERE entries_fts MATCH ? ORDER BY rank",
  )
    .bind(query)
    .all<{ id: string }>();
  return results.map((r) => r.id);
}

describe("entries schema + FTS5 triggers", () => {
  beforeEach(async () => {
    await env.DB.prepare("DELETE FROM entries").run();
  });

  it("inserts and reads back an entry", async () => {
    const db = getDb(env.DB);
    const id = crypto.randomUUID();
    await db.insert(entries).values({
      id,
      entryDate: "2026-06-23",
      title: "美好的一天",
      body: "今天天气很好，我很开心。",
      mood: 5,
    });

    const [row] = await db.select().from(entries).where(eq(entries.id, id));
    expect(row.title).toBe("美好的一天");
    expect(row.mood).toBe(5);
    expect(row.isPinned).toBe(false);
    expect(row.createdAt).toBeInstanceOf(Date);
  });

  it("indexes CJK text for full-text search on insert", async () => {
    const db = getDb(env.DB);
    const id = crypto.randomUUID();
    await db.insert(entries).values({
      id,
      entryDate: "2026-06-23",
      title: "美好的一天",
      body: "今天天气很好，我很开心。",
    });

    expect(await ftsMatch("很开心")).toContain(id);
    expect(await ftsMatch("不存在")).not.toContain(id);
  });

  it("keeps the FTS index in sync on update and delete", async () => {
    const db = getDb(env.DB);
    const id = crypto.randomUUID();
    await db.insert(entries).values({
      id,
      entryDate: "2026-06-23",
      title: "原始标题",
      body: "第一段内容关于跑步。",
    });
    expect(await ftsMatch("关于跑步")).toContain(id);

    await db.update(entries).set({ body: "改写后的内容关于游泳。" }).where(eq(entries.id, id));
    expect(await ftsMatch("关于跑步")).not.toContain(id);
    expect(await ftsMatch("关于游泳")).toContain(id);

    await db.delete(entries).where(eq(entries.id, id));
    expect(await ftsMatch("关于游泳")).not.toContain(id);
  });
});
