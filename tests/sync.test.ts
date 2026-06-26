import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { getDb } from "../app/lib/db/client";
import { createEntry, deleteEntry } from "../app/lib/db/entries";
import { EMPTY_CURSOR, getChangesSince } from "../app/lib/db/sync";
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
  });

  it("returns changes after the cursor, ordered by the (updatedAt, id) keyset", async () => {
    const a = await createEntry(db, { entryDate: "2026-06-20", body: "x" });
    const b = await createEntry(db, { entryDate: "2026-06-21", body: "y" });
    await setUpdatedAt(a, 1000);
    await setUpdatedAt(b, 2000);

    const all = await getChangesSince(db, EMPTY_CURSOR);
    expect(all.entries.map((e) => e.id)).toEqual([a, b]);
    expect(all.cursor.entries).toEqual({ updatedAt: 2000, id: b });
    expect(all.hasMore).toBe(false);

    // Re-using the returned cursor delivers nothing new.
    const afterAll = await getChangesSince(db, all.cursor);
    expect(afterAll.entries).toHaveLength(0);
  });

  it("does not skip rows that share the last page row's millisecond (keyset tie-break)", async () => {
    // Three entries with the *same* updatedAt; a bare `updatedAt > cursor` cursor
    // would drop the third one once the first page ends on that millisecond.
    const ids = [
      await createEntry(db, { entryDate: "2026-06-20", body: "x" }),
      await createEntry(db, { entryDate: "2026-06-20", body: "x" }),
      await createEntry(db, { entryDate: "2026-06-20", body: "x" }),
    ];
    for (const id of ids) {
      await setUpdatedAt(id, 1000);
    }
    const sorted = [...ids].sort();

    const page1 = await getChangesSince(db, EMPTY_CURSOR, 2);
    expect(page1.entries.map((e) => e.id)).toEqual(sorted.slice(0, 2));
    expect(page1.hasMore).toBe(true);

    const page2 = await getChangesSince(db, page1.cursor, 2);
    expect(page2.entries.map((e) => e.id)).toEqual(sorted.slice(2));

    // Every row delivered exactly once across the two pages.
    const seen = [...page1.entries, ...page2.entries].map((e) => e.id).sort();
    expect(seen).toEqual(sorted);
  });

  it("includes soft-deleted entries so clients can mirror removals", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-20",
      body: "x",
    });
    await deleteEntry(db, id);

    const changes = await getChangesSince(db, EMPTY_CURSOR);
    expect(changes.entries).toHaveLength(1);
    expect(changes.entries[0]?.deletedAt).not.toBeNull();
  });

  it("surfaces changed attachments with their own keyset cursor", async () => {
    const att = await putAttachment(db, env.BLOBS, KEY, {
      bytes: new TextEncoder().encode("x") as Uint8Array<ArrayBuffer>,
      filename: "f.png",
      contentType: "image/png",
    });

    const changes = await getChangesSince(db, EMPTY_CURSOR);
    expect(changes.attachments.map((a) => a.id)).toContain(att.id);
    expect(changes.cursor.attachments?.id).toBe(att.id);
  });

  it("flags hasMore when a page is full", async () => {
    const a = await createEntry(db, { entryDate: "2026-06-20", body: "x" });
    const b = await createEntry(db, { entryDate: "2026-06-21", body: "y" });
    await setUpdatedAt(a, 1000);
    await setUpdatedAt(b, 2000);

    const page = await getChangesSince(db, EMPTY_CURSOR, 1);
    expect(page.entries).toHaveLength(1);
    expect(page.hasMore).toBe(true);
  });
});
