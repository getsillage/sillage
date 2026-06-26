import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { getDb } from "../app/lib/db/client";
import { createEntry, getEntry, updateEntry } from "../app/lib/db/entries";
import { listEntryRevisions, recordEntryRevision } from "../app/lib/db/revisions";

const db = getDb(env.DB);

async function resetDb() {
  await env.DB.prepare("DELETE FROM entry_revisions").run();
  await env.DB.prepare("DELETE FROM entries").run();
}

describe("entry revisions", () => {
  beforeEach(resetDb);

  it("records a v1 snapshot on create", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-24",
      body: "最初的内容",
    });

    const revisions = await listEntryRevisions(db, id);
    expect(revisions).toHaveLength(1);
    expect(revisions[0].version).toBe(1);
    expect(revisions[0].entryDate).toBe("2026-06-24");
    expect(revisions[0].body).toBe("最初的内容");
    expect(revisions[0].createdAt).toBeInstanceOf(Date);
  });

  it("appends a snapshot on each update, newest version first", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-24",
      body: "v1",
    });

    const first = await updateEntry(db, id, {
      entryDate: "2026-06-24",
      body: "v2",
    });
    expect(first.status).toBe("updated");

    const second = await updateEntry(db, id, {
      entryDate: "2026-06-25",
      body: "v3",
    });
    expect(second.status).toBe("updated");

    const revisions = await listEntryRevisions(db, id);
    expect(revisions.map((revision) => revision.version)).toEqual([3, 2, 1]);
    expect(revisions.map((revision) => revision.body)).toEqual(["v3", "v2", "v1"]);
    expect(revisions.map((revision) => revision.entryDate)).toEqual([
      "2026-06-25",
      "2026-06-24",
      "2026-06-24",
    ]);

    const entry = await getEntry(db, id);
    expect(entry?.version).toBe(3);
    expect(revisions.length - 1).toBe((entry?.version ?? 0) - 1);
  });

  it("does not record a revision when an update conflicts", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-24",
      body: "v1",
    });

    const conflict = await updateEntry(db, id, { entryDate: "2026-06-24", body: "v2" }, 99);
    expect(conflict.status).toBe("conflict");
    expect(await listEntryRevisions(db, id)).toHaveLength(1);
  });

  it("records via recordEntryRevision with a default timestamp", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-24",
      body: "正文",
    });
    await recordEntryRevision(db, id, 99, {
      entryDate: "2026-06-24",
      body: "手动",
    });
    const revisions = await listEntryRevisions(db, id);
    expect(revisions[0].version).toBe(99);
    expect(revisions[0].entryDate).toBe("2026-06-24");
    expect(revisions[0].createdAt).toBeInstanceOf(Date);
  });

  it("cascade-deletes revisions when the entry is purged", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-24",
      body: "正文",
    });
    expect(await listEntryRevisions(db, id)).toHaveLength(1);

    await env.DB.prepare("DELETE FROM entries WHERE id = ?").bind(id).run();
    expect(await listEntryRevisions(db, id)).toHaveLength(0);
  });
});
