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

const db = getDb(env.DB);

describe("entries repository", () => {
  beforeEach(async () => {
    await env.DB.prepare("DELETE FROM entries").run();
  });

  it("creates an entry with date and body", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      body: "正文内容",
    });

    const entry = await getEntry(db, id);
    expect(entry?.entryDate).toBe("2026-06-23");
    expect(entry?.body).toBe("正文内容");
    expect(entry?.version).toBe(1);
    expect(entry?.summary).toBeNull();
  });

  it("updates date and body", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      body: "原内容",
    });

    const ok = await updateEntry(db, id, {
      entryDate: "2026-06-24",
      body: "新内容",
    });

    expect(ok).toEqual({ status: "updated", version: 2 });
    const entry = await getEntry(db, id);
    expect(entry?.entryDate).toBe("2026-06-24");
    expect(entry?.body).toBe("新内容");
  });

  it("returns missing when updating a missing entry", async () => {
    const ok = await updateEntry(db, "does-not-exist", {
      entryDate: "2026-06-23",
      body: "y",
    });
    expect(ok).toEqual({ status: "missing" });
  });

  it("lists entries newest-first by entry date", async () => {
    await createEntry(db, { entryDate: "2026-06-20", body: "早" });
    await createEntry(db, { entryDate: "2026-06-23", body: "晚" });

    const list = await listEntries(db);
    expect(list.map((e) => e.body)).toEqual(["晚", "早"]);
  });

  it("soft-deletes an entry: hidden from reads but tombstoned for sync/undo", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      body: "内容",
    });
    await deleteEntry(db, id);

    expect(await getEntry(db, id)).toBeNull();
    expect(await listEntries(db)).toHaveLength(0);

    const { results } = await env.DB.prepare(
      "SELECT deleted_at AS deletedAt FROM entries WHERE id = ?",
    )
      .bind(id)
      .all<{ deletedAt: number | null }>();
    expect(results[0]?.deletedAt).not.toBeNull();
  });

  it("restores a soft-deleted entry", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      body: "可恢复",
    });
    await deleteEntry(db, id);
    await restoreEntry(db, id);

    const entry = await getEntry(db, id);
    expect(entry?.body).toBe("可恢复");
  });

  it("rejects a stale update with a version conflict", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      body: "原内容",
    });

    const first = await updateEntry(db, id, { entryDate: "2026-06-23", body: "x" }, 1);
    expect(first).toEqual({ status: "updated", version: 2 });

    const stale = await updateEntry(db, id, { entryDate: "2026-06-23", body: "y" }, 1);
    expect(stale).toEqual({ status: "conflict", currentVersion: 2 });

    const entry = await getEntry(db, id);
    expect(entry?.body).toBe("x");
  });

  it("purges an entry permanently", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      body: "x",
    });
    await purgeEntry(db, id);

    const { results } = await env.DB.prepare("SELECT count(*) AS n FROM entries WHERE id = ?")
      .bind(id)
      .all<{ n: number }>();
    expect(results[0]?.n).toBe(0);
  });
});
