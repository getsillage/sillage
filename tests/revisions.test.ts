import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { getDb } from "../app/lib/db/client";
import { createEntry, getEntry, updateEntry } from "../app/lib/db/entries";
import { uuidv7 } from "../app/lib/db/id";
import { listEntryRevisions, recordEntryRevision } from "../app/lib/db/revisions";

const db = getDb(env.DB);

async function resetDb() {
  await env.DB.prepare("DELETE FROM entry_revisions").run();
  await env.DB.prepare("DELETE FROM entry_tags").run();
  await env.DB.prepare("DELETE FROM entries").run();
  await env.DB.prepare("DELETE FROM tags").run();
}

describe("entry revisions", () => {
  beforeEach(resetDb);

  it("records a v1 snapshot on create", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-24",
      title: "第一版",
      body: "最初的内容",
      mood: 4,
      location: "家",
      people: ["小明"],
      relationships: ["朋友"],
      tags: ["生活"],
    });

    const revisions = await listEntryRevisions(db, id);
    expect(revisions).toHaveLength(1);
    expect(revisions[0].version).toBe(1);
    expect(revisions[0].title).toBe("第一版");
    expect(revisions[0].body).toBe("最初的内容");
    expect(revisions[0].fields.tags).toEqual(["生活"]);
    expect(revisions[0].fields.people).toEqual(["小明"]);
    expect(revisions[0].fields.mood).toBe(4);
    expect(revisions[0].fields.location).toBe("家");
    expect(revisions[0].createdAt).toBeInstanceOf(Date);
  });

  it("appends a snapshot on each update, newest version first", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-24",
      title: "标题",
      body: "v1",
      tags: [],
    });

    const first = await updateEntry(db, id, {
      entryDate: "2026-06-24",
      title: "标题",
      body: "v2",
      tags: ["改动"],
    });
    expect(first.status).toBe("updated");

    const second = await updateEntry(db, id, {
      entryDate: "2026-06-24",
      title: "标题(改名)",
      body: "v3",
      tags: ["改动"],
    });
    expect(second.status).toBe("updated");

    const revisions = await listEntryRevisions(db, id);
    expect(revisions.map((revision) => revision.version)).toEqual([3, 2, 1]);
    expect(revisions.map((revision) => revision.body)).toEqual(["v3", "v2", "v1"]);

    const entry = await getEntry(db, id);
    expect(entry?.version).toBe(3);
    // Edit count derives from version: revisions == version, edits == version - 1.
    expect(revisions.length - 1).toBe((entry?.version ?? 0) - 1);
  });

  it("does not record a revision when an update conflicts", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-24",
      title: "标题",
      body: "v1",
      tags: [],
    });

    const conflict = await updateEntry(
      db,
      id,
      { entryDate: "2026-06-24", title: "标题", body: "v2", tags: [] },
      99, // stale expected version
    );
    expect(conflict.status).toBe("conflict");
    expect(await listEntryRevisions(db, id)).toHaveLength(1);
  });

  it("records via recordEntryRevision with a default timestamp", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-24",
      title: "标题",
      body: "正文",
      tags: [],
    });
    await recordEntryRevision(db, id, 99, {
      entryDate: "2026-06-24",
      title: "手动快照",
      body: "手动",
      mood: null,
      moodText: null,
      weather: null,
      location: null,
      people: [],
      relationships: [],
      tags: [],
    });
    const revisions = await listEntryRevisions(db, id);
    expect(revisions[0].version).toBe(99);
    expect(revisions[0].title).toBe("手动快照");
    expect(revisions[0].createdAt).toBeInstanceOf(Date);
  });

  it("falls back to default fields when the snapshot JSON is malformed", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-24",
      title: "标题",
      body: "正文",
      tags: [],
    });
    await env.DB.prepare(
      "INSERT INTO entry_revisions (id, entry_id, version, title, body, fields, created_at) VALUES (?,?,?,?,?,?,?)",
    )
      .bind(uuidv7(), id, 7, "坏数据", "x", "{not valid json", Date.now())
      .run();

    await env.DB.prepare(
      "INSERT INTO entry_revisions (id, entry_id, version, title, body, fields, created_at) VALUES (?,?,?,?,?,?,?)",
    )
      .bind(uuidv7(), id, 8, "无字段", "y", null, Date.now())
      .run();

    const revisions = await listEntryRevisions(db, id);
    const malformed = revisions.find((revision) => revision.version === 7);
    const nullFields = revisions.find((revision) => revision.version === 8);
    expect(malformed?.fields.tags).toEqual([]);
    expect(malformed?.fields.mood).toBeNull();
    expect(nullFields?.fields.tags).toEqual([]);
    expect(nullFields?.fields.entryDate).toBe("");
  });

  it("cascade-deletes revisions when the entry is purged", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-24",
      title: "标题",
      body: "正文",
      tags: [],
    });
    expect(await listEntryRevisions(db, id)).toHaveLength(1);

    await env.DB.prepare("DELETE FROM entries WHERE id = ?").bind(id).run();
    expect(await listEntryRevisions(db, id)).toHaveLength(0);
  });
});
