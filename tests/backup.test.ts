import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { exportDiaryBackup } from "../app/lib/backup/export";
import { getDb } from "../app/lib/db/client";
import { createEntry } from "../app/lib/db/entries";
import { putAttachment } from "../app/lib/storage/attachments";

const db = getDb(env.DB);
const KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

function bytes(text: string): Uint8Array<ArrayBuffer> {
  return new TextEncoder().encode(text) as Uint8Array<ArrayBuffer>;
}

describe("diary backup export", () => {
  beforeEach(async () => {
    await env.DB.prepare("DELETE FROM attachments").run();
    await env.DB.prepare("DELETE FROM entries").run();
    await env.DB.prepare("DELETE FROM tags").run();
  });

  it("writes JSON and Markdown backups to R2", async () => {
    const entryId = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "海边散步",
      body: "今天看到了漂亮夕阳。",
      mood: 5,
      weather: "晴",
      tags: ["旅行", "生活"],
    });
    await putAttachment(db, env.BLOBS, KEY, {
      entryId,
      bytes: bytes("image"),
      filename: "photo.png",
      contentType: "image/png",
    });

    const exportedAt = new Date("2026-06-24T03:04:05.000Z");
    const result = await exportDiaryBackup(env, exportedAt);

    expect(result).toEqual({
      jsonKey: "backups/2026-06-24/diary-2026-06-24T03-04-05-000Z.json",
      markdownKey: "backups/2026-06-24/diary-2026-06-24T03-04-05-000Z.md",
      entryCount: 1,
    });

    const jsonObject = await env.BLOBS.get(result.jsonKey);
    const markdownObject = await env.BLOBS.get(result.markdownKey);
    expect(jsonObject?.httpMetadata?.contentType).toBe("application/json; charset=utf-8");
    expect(markdownObject?.httpMetadata?.contentType).toBe("text/markdown; charset=utf-8");

    const payload = await jsonObject?.json<{
      version: 1;
      exportedAt: string;
      entries: Array<{ id: string; title: string; tags: string[] }>;
      attachments: Array<{ filename: string; r2Key: string }>;
    }>();
    expect(payload?.exportedAt).toBe("2026-06-24T03:04:05.000Z");
    expect(payload?.entries[0]).toMatchObject({
      id: entryId,
      title: "海边散步",
      tags: ["旅行", "生活"],
    });
    expect(payload?.attachments[0]?.filename).toBe("photo.png");
    expect(await markdownObject?.text()).toContain("## 2026-06-23 海边散步");
  });
});
