import { env } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { exportSillageBackup, runScheduledBackup } from "../app/lib/backup/export";
import { getDb } from "../app/lib/db/client";
import { createEntry } from "../app/lib/db/entries";
import { putAttachment } from "../app/lib/storage/attachments";

const db = getDb(env.DB);
const KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

function bytes(text: string): Uint8Array<ArrayBuffer> {
  return new TextEncoder().encode(text) as Uint8Array<ArrayBuffer>;
}

describe("Sillage backup export", () => {
  beforeEach(async () => {
    await env.DB.prepare("DELETE FROM attachments").run();
    await env.DB.prepare("DELETE FROM entries").run();
    await env.DB.prepare("DELETE FROM tags").run();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("writes JSON and Markdown backups to R2", async () => {
    const entryId = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "海边散步",
      body: "今天看到了漂亮夕阳。",
      kind: "note",
      noteType: "daily",
      mood: 5,
      moodText: "很明亮，也有一点想念",
      weather: "晴",
      location: "海边",
      people: ["朋友"],
      relationships: ["朋友"],
      tags: ["旅行", "生活"],
    });
    await putAttachment(db, env.BLOBS, KEY, {
      entryId,
      bytes: bytes("image"),
      filename: "photo.png",
      contentType: "image/png",
    });

    const exportedAt = new Date("2026-06-24T03:04:05.000Z");
    const result = await exportSillageBackup(env, exportedAt);

    expect(result).toEqual({
      jsonKey: "backups/2026-06-24/sillage-2026-06-24T03-04-05-000Z.json",
      markdownKey: "backups/2026-06-24/sillage-2026-06-24T03-04-05-000Z.md",
      entryCount: 1,
    });

    const jsonObject = await env.BLOBS.get(result.jsonKey);
    const markdownObject = await env.BLOBS.get(result.markdownKey);
    expect(jsonObject?.httpMetadata?.contentType).toBe("application/json; charset=utf-8");
    expect(markdownObject?.httpMetadata?.contentType).toBe("text/markdown; charset=utf-8");

    const payload = await jsonObject?.json<{
      version: 1;
      exportedAt: string;
      entries: Array<{
        id: string;
        title: string;
        kind: string;
        noteType: string | null;
        moodText: string | null;
        location: string | null;
        people: string[];
        relationships: string[];
        tags: string[];
      }>;
      attachments: Array<{ filename: string; r2Key: string }>;
    }>();
    expect(payload?.exportedAt).toBe("2026-06-24T03:04:05.000Z");
    expect(payload?.entries[0]).toMatchObject({
      id: entryId,
      title: "海边散步",
      kind: "note",
      noteType: "daily",
      moodText: "很明亮，也有一点想念",
      location: "海边",
      people: ["朋友"],
      relationships: ["朋友"],
      tags: ["旅行", "生活"],
    });
    expect(payload?.attachments[0]?.filename).toBe("photo.png");
    expect(await markdownObject?.text()).toContain("## 2026-06-23 海边散步");
  });

  it("renders summary/sentiment lines and falls back to the date for untitled entries", async () => {
    const entryId = await createEntry(db, {
      entryDate: "2026-06-20",
      title: "",
      body: "无标题正文",
      tags: [],
    });
    await env.DB.prepare("INSERT INTO entry_ai (entry_id, summary, sentiment) VALUES (?, ?, ?)")
      .bind(entryId, "一段摘要", "积极")
      .run();

    const { markdownKey } = await exportSillageBackup(env, new Date("2026-06-21T00:00:00.000Z"));
    const markdown = (await (await env.BLOBS.get(markdownKey))?.text()) ?? "";

    // Untitled entry → heading falls back to the entry date.
    expect(markdown).toContain("## 2026-06-20 2026-06-20");
    expect(markdown).toContain("摘要：一段摘要");
    expect(markdown).toContain("情绪：积极");
    // No tags/mood/weather were set, so those lines are omitted.
    expect(markdown).not.toContain("标签：");
    expect(markdown).not.toContain("心情：");
    expect(markdown).not.toContain("天气：");
  });

  it("runScheduledBackup writes a backup on success", async () => {
    await createEntry(db, { entryDate: "2026-06-19", title: "T", body: "B", tags: [] });

    await expect(runScheduledBackup(env)).resolves.toBeUndefined();

    const listing = await env.BLOBS.list({ prefix: "backups/" });
    expect(listing.objects.length).toBeGreaterThan(0);
  });

  it("runScheduledBackup logs context and rethrows when R2 fails", async () => {
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    const failingEnv = {
      ...env,
      BLOBS: {
        put: async () => {
          throw new Error("r2 unavailable");
        },
      },
    } as unknown as Env;

    await expect(runScheduledBackup(failingEnv)).rejects.toThrow("r2 unavailable");
    expect(errorSpy).toHaveBeenCalledOnce();
    expect(String(errorSpy.mock.calls[0]?.[0])).toContain("sillage-backup");
  });
});
