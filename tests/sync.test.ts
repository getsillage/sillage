import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { getDb } from "../app/lib/db/client";
import { createEntry, deleteEntry } from "../app/lib/db/entries";
import { getChangesSince } from "../app/lib/db/sync";
import { putAttachment } from "../app/lib/storage/attachments";

const KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
const db = getDb(env.DB);

async function setUpdatedAt(id: string, ms: number): Promise<void> {
  await env.DB.prepare("UPDATE entries SET updated_at = ? WHERE id = ?").bind(ms, id).run();
}

describe("delta sync", () => {
  beforeEach(async () => {
    await env.DB.prepare("DELETE FROM attachments").run();
    await env.DB.prepare("DELETE FROM entry_ai").run();
    await env.DB.prepare("DELETE FROM entries").run();
    await env.DB.prepare("DELETE FROM tags").run();
  });

  it("returns only changes strictly after the cursor, ordered by watermark", async () => {
    const a = await createEntry(db, { entryDate: "2026-06-20", title: "A", body: "x", tags: [] });
    const b = await createEntry(db, { entryDate: "2026-06-21", title: "B", body: "y", tags: [] });
    await setUpdatedAt(a, 1000);
    await setUpdatedAt(b, 2000);

    const all = await getChangesSince(db, new Date(0));
    expect(all.entries.map((e) => e.id)).toEqual([a, b]);
    expect(all.cursor).toBe(2000);
    expect(all.hasMore).toBe(false);

    const afterA = await getChangesSince(db, new Date(1000));
    expect(afterA.entries.map((e) => e.id)).toEqual([b]);
  });

  it("includes soft-deleted entries so clients can mirror removals", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-20",
      title: "待删除",
      body: "x",
      tags: [],
    });
    await deleteEntry(db, id);

    const changes = await getChangesSince(db, new Date(0));
    expect(changes.entries).toHaveLength(1);
    expect(changes.entries[0]?.deletedAt).not.toBeNull();
  });

  it("surfaces changed attachments", async () => {
    const att = await putAttachment(db, env.BLOBS, KEY, {
      bytes: new TextEncoder().encode("x") as Uint8Array<ArrayBuffer>,
      filename: "f.png",
      contentType: "image/png",
    });

    const changes = await getChangesSince(db, new Date(0));
    expect(changes.attachments.map((a) => a.id)).toContain(att.id);
  });

  it("flags hasMore when a page is full", async () => {
    const a = await createEntry(db, { entryDate: "2026-06-20", title: "A", body: "x", tags: [] });
    const b = await createEntry(db, { entryDate: "2026-06-21", title: "B", body: "y", tags: [] });
    await setUpdatedAt(a, 1000);
    await setUpdatedAt(b, 2000);

    const page = await getChangesSince(db, new Date(0), 1);
    expect(page.entries).toHaveLength(1);
    expect(page.hasMore).toBe(true);
  });
});
